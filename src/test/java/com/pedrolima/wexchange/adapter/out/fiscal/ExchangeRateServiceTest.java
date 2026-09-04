package com.pedrolima.wexchange.adapter.out.fiscal;

import com.pedrolima.wexchange.adapter.out.persistence.ExchangeRateUpsertRepository;
import com.pedrolima.wexchange.application.port.ExchangeRateQuote;
import com.pedrolima.wexchange.application.port.FiscalDataClient;
import com.pedrolima.wexchange.domain.exchange.ConversionWindow;
import com.pedrolima.wexchange.domain.money.Money;
import com.pedrolima.wexchange.domain.purchase.Purchase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fetching, retry, circuit-breaker, bulkhead, and deadline behaviour all moved
 * to {@link HttpFiscalDataClient} (issue #3); its dedicated test suite covers
 * that. Deduplication and existence-checking are gone too (issue #6): a bulk
 * {@code ON CONFLICT} upsert makes both the DB's problem, not this service's -
 * see {@link ExchangeRateUpsertRepository}'s own tests for that behaviour.
 * What remains here is this service's own job: fetch for the purchase's exact
 * window and hand the result to the upsert repository unchanged.
 */
@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    private static final String A_PURCHASE_ID = "5b1d9c04-7e32-4a86-b0f9-2c6a8d3e1b47";
    private static final Instant A_FIXED_INSTANT = Instant.parse("2024-07-15T12:00:00Z");
    private static final LocalDate A_PURCHASE_DATE = LocalDate.of(2024, 7, 15);

    @Mock
    private FiscalDataClient fiscalDataClient;

    @Mock
    private ExchangeRateUpsertRepository exchangeRateUpsertRepository;

    private ExchangeRateService exchangeRateService;

    private static Purchase aPurchase() {
        return Purchase.create(
                A_PURCHASE_ID, "Description", A_PURCHASE_DATE, Money.of("100.00"), A_FIXED_INSTANT);
    }

    @BeforeEach
    void setUp() {
        exchangeRateService = new ExchangeRateService(fiscalDataClient, exchangeRateUpsertRepository);
    }

    @Test
    void givenFetchedQuotes_whenRefreshing_thenTheyAreUpsertedUnchanged() {
        final var purchase = aPurchase();
        final var quote = new ExchangeRateQuote("Brazil-Real", LocalDate.of(2023, 10, 30), new BigDecimal("5.22"));
        when(fiscalDataClient.fetchExchangeRates(any(ConversionWindow.class))).thenReturn(List.of(quote));

        exchangeRateService.refreshFor(purchase);

        final var upserted = ArgumentCaptor.forClass(List.class);
        verify(exchangeRateUpsertRepository).upsertAll(upserted.capture());
        Assertions.assertEquals(List.of(quote), upserted.getValue());
    }

    @Test
    void givenTheWindowOnThePurchase_whenRefreshing_thenTheClientIsAskedForExactlyThatWindow() {
        final var purchase = aPurchase();
        when(fiscalDataClient.fetchExchangeRates(any(ConversionWindow.class))).thenReturn(List.of());

        exchangeRateService.refreshFor(purchase);

        final var window = ArgumentCaptor.forClass(ConversionWindow.class);
        verify(fiscalDataClient).fetchExchangeRates(window.capture());
        Assertions.assertEquals(purchase.conversionWindow(), window.getValue());
    }

    @Test
    void givenNoQuotesFetched_whenRefreshing_thenAnEmptyUpsertIsStillDelegated() {
        final var purchase = aPurchase();
        when(fiscalDataClient.fetchExchangeRates(any(ConversionWindow.class))).thenReturn(List.of());

        exchangeRateService.refreshFor(purchase);

        verify(exchangeRateUpsertRepository, times(1)).upsertAll(List.of());
    }

    @Test
    void givenTheClientHasExhaustedItsOwnRetries_whenRefreshing_thenTheFailurePropagatesWithoutARetryHere() {
        final var purchase = aPurchase();
        final var failure = new com.pedrolima.wexchange.domain.error.RetryableException("Fiscal data API is unavailable");
        when(fiscalDataClient.fetchExchangeRates(any(ConversionWindow.class))).thenThrow(failure);

        Assertions.assertThrows(
                com.pedrolima.wexchange.domain.error.RetryableException.class,
                () -> exchangeRateService.refreshFor(purchase));

        verify(fiscalDataClient, times(1)).fetchExchangeRates(any(ConversionWindow.class));
        verify(exchangeRateUpsertRepository, never()).upsertAll(any());
    }
}
