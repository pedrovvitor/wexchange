package com.pedrolima.wexchange.adapter.out.fiscal;

import com.pedrolima.wexchange.adapter.out.persistence.CountryCurrencyJpaEntity;
import com.pedrolima.wexchange.adapter.out.persistence.CountryCurrencyRepository;
import com.pedrolima.wexchange.application.port.CountryCurrencyRecord;
import com.pedrolima.wexchange.application.port.FiscalDataClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Periodically synchronizes the set of known country currencies from the
 * fiscal data provider.
 *
 * <p>Fetching, and every retry/circuit-breaker/bulkhead/timeout decision that
 * goes with it, lives in {@link FiscalDataClient} (issue #3). This method does
 * not retry on top of that: a failed run simply waits for the next scheduled
 * tick rather than stacking a second retry policy over the client's own.
 */
@Service
@Slf4j
public class CountryCurrencyUpdaterService {

    public static final int ONE_DAY_MS = 86400000;

    private final CountryCurrencyRepository repository;
    private final FiscalDataClient fiscalDataClient;
    private final MetricsHelper metricsHelper;

    public CountryCurrencyUpdaterService(
            final CountryCurrencyRepository repository,
            final FiscalDataClient fiscalDataClient,
            final MetricsHelper metricsHelper
    ) {
        this.repository = repository;
        this.fiscalDataClient = fiscalDataClient;
        this.metricsHelper = metricsHelper;
    }

    @Scheduled(fixedRate = ONE_DAY_MS, initialDelay = 100)
    public void synchronizeCountryCurrencies() {
        final StopWatch watch = new StopWatch();
        watch.start();
        final List<CountryCurrencyRecord> countryCurrencies = fiscalDataClient.fetchCountryCurrencies();
        saveCountryCurrencies(countryCurrencies);
        metricsHelper.registryUpsertCountryCurrenciesElapsedTime(watch.getNanoTime());
        log.debug("Processing and saving all Country Currencies successfully. Elapsed time {}", watch.formatTime());
    }

    private void saveCountryCurrencies(final List<CountryCurrencyRecord> countryCurrencies) {
        final var fiscalDataApiCountryCurrencies =
                countryCurrencies.stream()
                        .filter(countryCurrency -> repository.notExistsByCountryCurrency(countryCurrency.countryCurrency()))
                        .map(cc -> CountryCurrencyJpaEntity.of(cc.countryCurrency(), cc.country(), cc.currency()))
                        .collect(Collectors.toList());

        repository.saveAll(fiscalDataApiCountryCurrencies);
    }
}
