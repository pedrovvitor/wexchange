package com.pedrolima.wexchange.adapter.out.fiscal;

import com.pedrolima.wexchange.adapter.out.persistence.CountryCurrencySyncRunRepository;
import com.pedrolima.wexchange.adapter.out.persistence.CountryCurrencySyncRunTracker;
import com.pedrolima.wexchange.adapter.out.persistence.CountryCurrencyUpsertRepository;
import com.pedrolima.wexchange.application.port.CountryCurrencyRecord;
import com.pedrolima.wexchange.application.port.FiscalDataClient;
import com.pedrolima.wexchange.domain.error.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fetching, retry, circuit-breaker, bulkhead, and deadline behaviour all moved
 * to {@link HttpFiscalDataClient} (issue #3). Persisting is a bulk upsert
 * (issue #6): {@link CountryCurrencyUpsertRepository}'s own tests cover that.
 * What remains here is this service's own job: only actually run when the
 * advisory lock is won, and record the outcome (running/succeeded/failed)
 * regardless of which way the run goes.
 */
@ExtendWith(MockitoExtension.class)
class CountryCurrencyUpdaterServiceTest {

    private static final CountryCurrencyRecord BRAZIL_REAL =
            new CountryCurrencyRecord("Brazil-Real", "Brazil", "Real");

    private static final Instant NOW = Instant.parse("2024-07-15T12:00:00Z");

    @Mock
    private CountryCurrencyUpsertRepository upsertRepository;

    @Mock
    private CountryCurrencySyncRunRepository lockRepository;

    @Mock
    private CountryCurrencySyncRunTracker runTracker;

    @Mock
    private FiscalDataClient fiscalDataClient;

    @Mock
    private MetricsHelper metricsHelper;

    @Mock
    private CountryCurrencySyncMetrics syncMetrics;

    private CountryCurrencyUpdaterService service;

    @BeforeEach
    void setUp() {
        service = new CountryCurrencyUpdaterService(
                upsertRepository, lockRepository, runTracker, fiscalDataClient, metricsHelper, syncMetrics,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("winning the lock runs the sync and records success")
    void givenTheLockIsWon_whenSynchronizing_thenTheFetchIsUpsertedAndSuccessIsRecorded() {
        when(lockRepository.tryAcquireSyncLock(CountryCurrencyUpdaterService.SYNC_LOCK_KEY)).thenReturn(true);
        when(fiscalDataClient.fetchCountryCurrencies()).thenReturn(List.of(BRAZIL_REAL));

        service.synchronizeCountryCurrencies();

        verify(upsertRepository).upsertAll(List.of(BRAZIL_REAL));
        verify(runTracker).recordRunning(NOW);
        verify(runTracker).recordSuccess(NOW, NOW);
        verify(syncMetrics).recordSuccess(NOW);
        verify(syncMetrics, never()).recordFailure(any());
        verify(metricsHelper, times(1)).registryUpsertCountryCurrenciesElapsedTime(anyLong());
    }

    @Test
    @DisplayName("losing the lock skips the run entirely, with nothing else touched")
    void givenAnotherInstanceHoldsTheLock_whenSynchronizing_thenThisRunIsSkipped() {
        when(lockRepository.tryAcquireSyncLock(CountryCurrencyUpdaterService.SYNC_LOCK_KEY)).thenReturn(false);

        service.synchronizeCountryCurrencies();

        verify(fiscalDataClient, never()).fetchCountryCurrencies();
        verify(upsertRepository, never()).upsertAll(any());
        verify(runTracker, never()).recordRunning(any());
        verify(runTracker, never()).recordSuccess(any(), any());
        verify(runTracker, never()).recordFailure(any(), any(), any());
    }

    @Test
    @DisplayName("a fetch failure is recorded and propagates, without ever upserting")
    void givenTheClientHasExhaustedItsOwnRetries_whenSynchronizing_thenTheFailureIsRecordedAndPropagates() {
        when(lockRepository.tryAcquireSyncLock(CountryCurrencyUpdaterService.SYNC_LOCK_KEY)).thenReturn(true);
        final var failure = new RetryableException("Fiscal data API is unavailable");
        when(fiscalDataClient.fetchCountryCurrencies()).thenThrow(failure);

        final var thrown = assertThrows(RetryableException.class, () -> service.synchronizeCountryCurrencies());

        assertEquals(failure, thrown);
        verify(runTracker).recordRunning(NOW);
        verify(runTracker).recordFailure(NOW, NOW, "Fiscal data API is unavailable");
        verify(runTracker, never()).recordSuccess(any(), any());
        verify(syncMetrics).recordFailure(NOW);
        verify(syncMetrics, never()).recordSuccess(any());
        verify(upsertRepository, never()).upsertAll(any());
    }
}
