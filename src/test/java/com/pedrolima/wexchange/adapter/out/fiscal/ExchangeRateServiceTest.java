package com.pedrolima.wexchange.adapter.out.fiscal;

import com.pedrolima.wexchange.adapter.out.persistence.ExchangeRateJpaEntity;
import com.pedrolima.wexchange.adapter.out.persistence.ExchangeRateRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fetching, retry, circuit-breaker, bulkhead, and deadline behaviour all moved
 * to {@link HttpFiscalDataClient} (issue #3); its dedicated test suite covers
 * that. What remains here is this service's own job: turn quotes the client
 * already fetched and validated into deduplicated, genuinely-new rows.
 */
@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    private static final String A_PURCHASE_ID = "5b1d9c04-7e32-4a86-b0f9-2c6a8d3e1b47";
    private static final Instant A_FIXED_INSTANT = Instant.parse("2024-07-15T12:00:00Z");
    private static final LocalDate A_PURCHASE_DATE = LocalDate.of(2024, 7, 15);

    @Mock
    private FiscalDataClient fiscalDataClient;

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    private ExchangeRateService exchangeRateService;

    private static Purchase aPurchase() {
        return Purchase.create(
                A_PURCHASE_ID, "Description", A_PURCHASE_DATE, Money.of("100.00"), A_FIXED_INSTANT);
    }

    @BeforeEach
    void setUp() {
        exchangeRateService = new ExchangeRateService(fiscalDataClient, exchangeRateRepository);
    }

    @Test
    void givenNewQuotes_whenRefreshing_thenTheyArePersisted() {
        final var purchase = aPurchase();
        final var quote = new ExchangeRateQuote("Brazil-Real", LocalDate.of(2023, 10, 30), new BigDecimal("5.22"));
        when(fiscalDataClient.fetchExchangeRates(any(ConversionWindow.class))).thenReturn(List.of(quote));
        when(exchangeRateRepository.notExistsByCountryCurrencyAndEffectiveDate("Brazil-Real", quote.effectiveDate()))
                .thenReturn(true);

        exchangeRateService.refreshFor(purchase);

        final var saved = ArgumentCaptor.forClass(List.class);
        verify(exchangeRateRepository).saveAll(saved.capture());
        Assertions.assertEquals(1, saved.getValue().size());
        final var stored = (ExchangeRateJpaEntity) saved.getValue().get(0);
        Assertions.assertEquals("Brazil-Real", stored.getCountryCurrency());
        Assertions.assertEquals(LocalDate.of(2023, 10, 30), stored.getEffectiveDate());
        Assertions.assertEquals(new BigDecimal("5.22"), stored.getRateValue());
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
    void givenAlreadyStoredQuote_whenRefreshing_thenNothingIsSaved() {
        final var purchase = aPurchase();
        final var quote = new ExchangeRateQuote("Brazil-Real", LocalDate.of(2023, 10, 30), new BigDecimal("5.22"));
        when(fiscalDataClient.fetchExchangeRates(any(ConversionWindow.class))).thenReturn(List.of(quote));
        when(exchangeRateRepository.notExistsByCountryCurrencyAndEffectiveDate(anyString(), any(LocalDate.class)))
                .thenReturn(false);

        exchangeRateService.refreshFor(purchase);

        verify(exchangeRateRepository, never()).saveAll(any());
    }

    @Test
    void givenDuplicateCountryCurrencyInTheSameBatch_whenRefreshing_thenOnlyTheFirstIsKept() {
        final var purchase = aPurchase();
        final var effectiveDate = LocalDate.of(2023, 10, 30);
        final var first = new ExchangeRateQuote("Brazil-Real", effectiveDate, new BigDecimal("5.22"));
        final var second = new ExchangeRateQuote("Brazil-Real", effectiveDate, new BigDecimal("9.99"));
        when(fiscalDataClient.fetchExchangeRates(any(ConversionWindow.class))).thenReturn(List.of(first, second));
        when(exchangeRateRepository.notExistsByCountryCurrencyAndEffectiveDate("Brazil-Real", effectiveDate))
                .thenReturn(true);

        exchangeRateService.refreshFor(purchase);

        final var saved = ArgumentCaptor.forClass(List.class);
        verify(exchangeRateRepository).saveAll(saved.capture());
        Assertions.assertEquals(1, saved.getValue().size());
        final var kept = (ExchangeRateJpaEntity) saved.getValue().get(0);
        Assertions.assertEquals(new BigDecimal("5.22"), kept.getRateValue());
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
        verify(exchangeRateRepository, never()).saveAll(any());
    }
}
