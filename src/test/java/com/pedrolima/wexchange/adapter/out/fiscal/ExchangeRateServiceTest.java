package com.pedrolima.wexchange.adapter.out.fiscal;

import com.pedrolima.wexchange.adapter.out.persistence.ExchangeRateJpaEntity;
import com.pedrolima.wexchange.adapter.out.persistence.ExchangeRateRepository;
import com.pedrolima.wexchange.domain.error.RetryableException;
import com.pedrolima.wexchange.domain.money.Money;
import com.pedrolima.wexchange.domain.purchase.Purchase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.retry.context.RetryContextSupport;
import org.springframework.retry.support.RetrySynchronizationManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ExchangeRateServiceTest {

    private static final String QUOTE = "\"";

    private static final String API_URL = "http://mocked.api.url";

    private static final String A_PURCHASE_ID = "5b1d9c04-7e32-4a86-b0f9-2c6a8d3e1b47";

    private static final Instant A_FIXED_INSTANT = Instant.parse("2024-07-15T12:00:00Z");

    private static final LocalDate A_PURCHASE_DATE = LocalDate.of(2024, 7, 15);

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private MetricsHelper metricsHelper;

    @Mock
    private HttpClient httpClient;

    private ExchangeRateService exchangeRateService;

    private HttpResponse<String> response;

    private static Purchase aPurchase() {
        return Purchase.create(
                A_PURCHASE_ID, "Description", A_PURCHASE_DATE, Money.of("100.00"), A_FIXED_INSTANT);
    }

    /** Wraps rate objects in the envelope the Fiscal Data API returns. */
    private static String ratesPayload(final String... rates) {
        return "{" + QUOTE + "data" + QUOTE + ":[" + String.join(",", rates) + "]}";
    }

    @BeforeEach
    void setUp() {
        response = Mockito.mock(HttpResponse.class);
        exchangeRateService = new ExchangeRateService(
                API_URL, exchangeRateRepository, metricsHelper, httpClient);
    }

    @Test
    void givenValidPurchase_whenCallUpdateExchangeRates_thenUpdateExchangeRates() throws Exception {
        final var purchase = aPurchase();
        final var conversionRate = ConversionRate.with(
                BigDecimal.valueOf(5.22),
                LocalDate.of(2023, 10, 30),
                "Brazil-Real");

        when(response.statusCode()).thenReturn(HttpStatus.OK.value());
        when(response.body()).thenReturn(ratesPayload("""
                {"exchange_rate":"5.22","effective_date":"2023-10-30","country_currency_desc":"Brazil-Real"}"""));

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandlers.ofString().getClass())))
                .thenReturn(response);
        when(exchangeRateRepository.notExistsByCountryCurrencyAndEffectiveDate(
                conversionRate.countryCurrency(),
                conversionRate.effectiveDate()))
                .thenReturn(true);

        exchangeRateService.refreshFor(purchase);

        final var saved = ArgumentCaptor.forClass(List.class);
        verify(exchangeRateRepository).saveAll(saved.capture());
        Assertions.assertEquals(1, saved.getValue().size());
        final var stored = (ExchangeRateJpaEntity) saved.getValue().get(0);
        Assertions.assertEquals("Brazil-Real", stored.getCountryCurrency());
        Assertions.assertEquals(LocalDate.of(2023, 10, 30), stored.getEffectiveDate());
        Assertions.assertEquals(new BigDecimal("5.22"), stored.getRateValue());
        verify(exchangeRateRepository, times(1))
                .notExistsByCountryCurrencyAndEffectiveDate(anyString(), any(LocalDate.class));
        verify(metricsHelper, times(1)).registryFiscalServiceRetrievalElapsedTime(anyLong());
    }

    @Test
    void givenValidPurchase_whenCallUpdateExchangeRatesAndNoNewConversionRate_thenDontSaveAll() throws Exception {
        final var purchase = aPurchase();
        final var conversionRate = ConversionRate.with(
                BigDecimal.valueOf(5.22),
                LocalDate.of(2023, 10, 30),
                "Brazil-Real");

        when(response.statusCode()).thenReturn(HttpStatus.OK.value());
        when(response.body()).thenReturn(ratesPayload("""
                {"exchange_rate":"5.22","effective_date":"2023-10-30","country_currency_desc":"Brazil-Real"}"""));

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandlers.ofString().getClass())))
                .thenReturn(response);
        when(exchangeRateRepository.notExistsByCountryCurrencyAndEffectiveDate(
                conversionRate.countryCurrency(),
                conversionRate.effectiveDate()))
                .thenReturn(false);

        exchangeRateService.refreshFor(purchase);

        verify(exchangeRateRepository, times(1))
                .notExistsByCountryCurrencyAndEffectiveDate(anyString(), any(LocalDate.class));
        verify(exchangeRateRepository, never()).saveAll(any());
        verify(metricsHelper, times(1)).registryFiscalServiceRetrievalElapsedTime(anyLong());
    }

    @Test
    void givenValidPurchase_whenCallUpdateExchangeRatesAndServiceUnavailable_thenThrowsRetryException() throws Exception {
        final var purchase = aPurchase();
        final var expectedExceptionMessage = "Unexpected response from API";

        when(response.statusCode()).thenReturn(HttpStatus.SERVICE_UNAVAILABLE.value());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandlers.ofString().getClass())))
                .thenReturn(response);

        final var actualException =
                Assertions.assertThrows(RetryableException.class, () -> exchangeRateService.refreshFor(purchase));

        Assertions.assertEquals(expectedExceptionMessage, actualException.getMessage());
        verify(exchangeRateRepository, never()).saveAll(any());
        verify(exchangeRateRepository, never()).saveAll(any());
        verify(metricsHelper, times(1)).registryFiscalServiceRetrievalElapsedTime(anyLong());
    }

    @Test
    void givenValidPurchase_whenCallUpdateExchangeRatesAndIoException_thenThrowsRetryException() throws Exception {
        final var purchase = aPurchase();
        final var expectedExceptionMessage = "Error processing request";

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandlers.ofString().getClass())))
                .thenThrow(IOException.class);

        final var actualException =
                Assertions.assertThrows(RetryableException.class, () -> exchangeRateService.refreshFor(purchase));

        Assertions.assertEquals(expectedExceptionMessage, actualException.getMessage());
        verify(exchangeRateRepository, never()).saveAll(any());
        verify(metricsHelper, times(1)).incrementRequestErrorMetric();
        verify(metricsHelper, never()).registryFiscalServiceRetrievalElapsedTime(anyLong());
    }

    @Test
    void givenValidPurchase_whenCallUpdateExchangeRates_thenTheUpstreamQueryPinsFieldsFilterSortAndPageSize()
            throws Exception {
        final var purchase = aPurchase();

        when(response.statusCode()).thenReturn(HttpStatus.SERVICE_UNAVAILABLE.value());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandlers.ofString().getClass())))
                .thenReturn(response);

        Assertions.assertThrows(RetryableException.class, () -> exchangeRateService.refreshFor(purchase));

        final var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(request.capture(), any(HttpResponse.BodyHandlers.ofString().getClass()));
        Assertions.assertEquals(
                "http://mocked.api.url"
                        + "?page[size]=10000"
                        + "&fields=exchange_rate,effective_date,country_currency_desc"
                        + "&filter=effective_date:gte:2024-01-15,effective_date:lte:2024-07-15"
                        + "&sort=-effective_date,-country_currency_desc",
                request.getValue().uri().toString());
    }

    @Test
    void givenDuplicatedCountryCurrencyInPayload_whenCallUpdateExchangeRates_thenOnlyTheFirstRateIsKept()
            throws Exception {
        final var purchase = aPurchase();
        final var effectiveDate = LocalDate.of(2023, 10, 30);

        when(response.statusCode()).thenReturn(HttpStatus.OK.value());
        when(response.body()).thenReturn(ratesPayload("""
                {"exchange_rate":"5.22","effective_date":"2023-10-30","country_currency_desc":"Brazil-Real"}""", """
                {"exchange_rate":"9.99","effective_date":"2023-10-30","country_currency_desc":"Brazil-Real"}"""));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandlers.ofString().getClass())))
                .thenReturn(response);
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
    void givenAnActiveRetryContext_whenCallUpdateExchangeRatesFails_thenTheAttemptIsReportedWithoutMaskingTheFailure()
            throws Exception {
        final var purchase = aPurchase();
        final var retryContext = new RetryContextSupport(null);
        retryContext.registerThrowable(new RetryableException("previous attempt"));
        RetrySynchronizationManager.register(retryContext);

        try {
            when(response.statusCode()).thenReturn(HttpStatus.SERVICE_UNAVAILABLE.value());
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandlers.ofString().getClass())))
                    .thenReturn(response);

            final var actualException = Assertions.assertThrows(
                    RetryableException.class, () -> exchangeRateService.refreshFor(purchase));

            Assertions.assertEquals("Unexpected response from API", actualException.getMessage());
            Assertions.assertEquals(1, retryContext.getRetryCount());
        } finally {
            RetrySynchronizationManager.clear();
        }
    }
}
