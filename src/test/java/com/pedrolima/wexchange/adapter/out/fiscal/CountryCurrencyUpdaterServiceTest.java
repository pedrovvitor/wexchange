package com.pedrolima.wexchange.adapter.out.fiscal;

import com.pedrolima.wexchange.adapter.out.persistence.CountryCurrencyJpaEntity;
import com.pedrolima.wexchange.adapter.out.persistence.CountryCurrencyRepository;
import com.pedrolima.wexchange.application.port.CountryCurrencyRecord;
import com.pedrolima.wexchange.application.port.FiscalDataClient;
import com.pedrolima.wexchange.domain.error.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fetching, retry, circuit-breaker, bulkhead, and deadline behaviour all moved
 * to {@link HttpFiscalDataClient} (issue #3); its dedicated test suite covers
 * that. What remains here is this service's own job: persist genuinely-new
 * country currencies from what the client already fetched and validated.
 */
@ExtendWith(MockitoExtension.class)
class CountryCurrencyUpdaterServiceTest {

    private static final CountryCurrencyRecord BRAZIL_REAL =
            new CountryCurrencyRecord("Brazil-Real", "Brazil", "Real");

    @Mock
    private CountryCurrencyRepository repository;

    @Mock
    private FiscalDataClient fiscalDataClient;

    @Mock
    private MetricsHelper metricsHelper;

    private CountryCurrencyUpdaterService countryCurrencyUpdaterService;

    @BeforeEach
    void setUp() {
        countryCurrencyUpdaterService = new CountryCurrencyUpdaterService(repository, fiscalDataClient, metricsHelper);
    }

    @Test
    void givenANewCountryCurrency_whenSynchronizing_thenItIsSaved() {
        when(fiscalDataClient.fetchCountryCurrencies()).thenReturn(List.of(BRAZIL_REAL));
        when(repository.notExistsByCountryCurrency("Brazil-Real")).thenReturn(true);

        countryCurrencyUpdaterService.synchronizeCountryCurrencies();

        final var saved = ArgumentCaptor.forClass(List.class);
        verify(repository, times(1)).saveAll(saved.capture());
        assertEquals(1, saved.getValue().size());
        final var stored = (CountryCurrencyJpaEntity) saved.getValue().get(0);
        assertEquals("Brazil-Real", stored.getCountryCurrency());
        assertEquals("Brazil", stored.getCountry());
        assertEquals("Real", stored.getCurrency());
        verify(metricsHelper, times(1)).registryUpsertCountryCurrenciesElapsedTime(anyLong());
    }

    @Test
    void givenAnAlreadyKnownCountryCurrency_whenSynchronizing_thenNothingNewIsPersisted() {
        when(fiscalDataClient.fetchCountryCurrencies()).thenReturn(List.of(BRAZIL_REAL));
        when(repository.notExistsByCountryCurrency("Brazil-Real")).thenReturn(false);

        countryCurrencyUpdaterService.synchronizeCountryCurrencies();

        final var saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        assertTrue(saved.getValue().isEmpty());
    }

    @Test
    void givenTheClientHasExhaustedItsOwnRetries_whenSynchronizing_thenTheFailurePropagatesWithoutARetryHere() {
        when(fiscalDataClient.fetchCountryCurrencies())
                .thenThrow(new RetryableException("Fiscal data API is unavailable"));

        assertThrows(RetryableException.class, () -> countryCurrencyUpdaterService.synchronizeCountryCurrencies());

        verify(repository, never()).saveAll(any());
    }
}
