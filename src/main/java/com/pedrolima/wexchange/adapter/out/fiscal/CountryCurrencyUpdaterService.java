package com.pedrolima.wexchange.adapter.out.fiscal;

import com.pedrolima.wexchange.adapter.out.persistence.CountryCurrencySyncRunRepository;
import com.pedrolima.wexchange.adapter.out.persistence.CountryCurrencySyncRunTracker;
import com.pedrolima.wexchange.adapter.out.persistence.CountryCurrencyUpsertRepository;
import com.pedrolima.wexchange.application.port.FiscalDataClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Periodically synchronizes the set of known country currencies from the
 * fiscal data provider.
 *
 * <p>Fetching, and every retry/circuit-breaker/bulkhead/timeout decision that
 * goes with it, lives in {@link FiscalDataClient} (issue #3). This method does
 * not retry on top of that: a failed run simply waits for the next scheduled
 * tick rather than stacking a second retry policy over the client's own.
 *
 * <p>Persisting is a single bulk upsert (issue #6), not a per-record
 * exists-check: see {@link CountryCurrencyUpsertRepository}. Because that
 * write is safe under concurrent execution by construction, the transaction-
 * scoped advisory lock acquired below exists purely to stop two replicas from
 * both hitting the upstream provider for the same scheduled tick - it is not
 * needed for data correctness, which the upsert already guarantees on its
 * own. The lock is held for this whole method's transaction, including the
 * upstream fetch: see docs/adr/0005-idempotent-exchange-rate-refresh.md for
 * why that tradeoff (one pooled connection held for the fetch's duration) was
 * accepted rather than narrowing the lock to just the write.
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "app.country-currency-sync", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CountryCurrencySyncProperties.class)
public class CountryCurrencyUpdaterService {

    /**
     * Arbitrary but fixed: every replica must agree on the same key for
     * {@code pg_try_advisory_xact_lock} to serialize them against each other.
     * Reserved for this job alone - never reuse for a different lock.
     */
    static final long SYNC_LOCK_KEY = 7_246_193_847L;

    private final CountryCurrencyUpsertRepository upsertRepository;
    private final CountryCurrencySyncRunRepository lockRepository;
    private final CountryCurrencySyncRunTracker runTracker;
    private final FiscalDataClient fiscalDataClient;
    private final MetricsHelper metricsHelper;
    private final CountryCurrencySyncMetrics syncMetrics;
    private final Clock clock;

    public CountryCurrencyUpdaterService(
            final CountryCurrencyUpsertRepository upsertRepository,
            final CountryCurrencySyncRunRepository lockRepository,
            final CountryCurrencySyncRunTracker runTracker,
            final FiscalDataClient fiscalDataClient,
            final MetricsHelper metricsHelper,
            final CountryCurrencySyncMetrics syncMetrics,
            final Clock clock
    ) {
        this.upsertRepository = upsertRepository;
        this.lockRepository = lockRepository;
        this.runTracker = runTracker;
        this.fiscalDataClient = fiscalDataClient;
        this.metricsHelper = metricsHelper;
        this.syncMetrics = syncMetrics;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.country-currency-sync.cron}", zone = "${app.country-currency-sync.zone}")
    @Transactional
    public void synchronizeCountryCurrencies() {
        if (!lockRepository.tryAcquireSyncLock(SYNC_LOCK_KEY)) {
            log.debug("Skipping country-currency sync: another instance already holds the lock for this run.");
            return;
        }

        final var startedAt = clock.instant();
        runTracker.recordRunning(startedAt);

        final StopWatch watch = new StopWatch();
        watch.start();
        try {
            final var countryCurrencies = fiscalDataClient.fetchCountryCurrencies();
            upsertRepository.upsertAll(countryCurrencies);
        } catch (final RuntimeException e) {
            final var finishedAt = clock.instant();
            runTracker.recordFailure(startedAt, finishedAt, e.getMessage());
            syncMetrics.recordFailure(finishedAt);
            throw e;
        }

        final var finishedAt = clock.instant();
        metricsHelper.registryUpsertCountryCurrenciesElapsedTime(watch.getNanoTime());
        runTracker.recordSuccess(startedAt, finishedAt);
        syncMetrics.recordSuccess(finishedAt);
        log.debug("Processing and saving all Country Currencies successfully. Elapsed time {}", watch.formatTime());
    }
}
