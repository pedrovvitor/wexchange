package com.pedrolima.wexchange.service;

import com.pedrolima.wexchange.entities.ExchangeRateJpaEntity;
import com.pedrolima.wexchange.entities.PurchaseJpaEntity;
import com.pedrolima.wexchange.exceptions.RetryableException;
import com.pedrolima.wexchange.integration.fiscal.beans.ConversionRate;
import com.pedrolima.wexchange.repositories.ExchangeRateRepository;
import com.pedrolima.wexchange.services.async.ExchangeRateService;
import com.pedrolima.wexchange.utils.JsonUtils;
import com.pedrolima.wexchange.utils.MetricsHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.retry.context.RetryContextSupport;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private MetricsHelper metricsHelper;

    @Mock
    private HttpClient httpClient;

    @InjectMocks
    private ExchangeRateService exchangeRateService;

    private HttpResponse<String> response;

    @BeforeEach
    void setUp() {
        response = Mockito.mock(HttpResponse.class);
        ReflectionTestUtils.setField(exchangeRateService, "exchangeApiUrl", "http://mocked.api.url");
    }

    @Test
    void givenValidPurchase_whenCallUpdateExchangeRates_thenUpdateExchangeRates() throws Exception {
        final var purchase = PurchaseJpaEntity.newPurchase("Description", LocalDate.now(), BigDecimal.valueOf(100));
        final var conversionRate = ConversionRate.with(
                BigDecimal.valueOf(5.22),
                LocalDate.of(2023, 10, 30),
                "Brazil-Real");

        when(response.statusCode()).thenReturn(HttpStatus.OK.value());
        when(response.body()).thenReturn("json response");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandlers.ofString().getClass())))
                .thenReturn(response);
        when(exchangeRateRepository.notExistsByCountryCurrencyAndEffectiveDate(
                conversionRate.countryCurrency(),
                conversionRate.effectiveDate()))
                .thenReturn(true);

        try (MockedStatic<JsonUtils> mockedJsonUtils = Mockito.mockStatic(JsonUtils.class)) {
            mockedJsonUtils.when(() -> JsonUtils.extractDataList(anyString(), any())).thenReturn(List.of(conversionRate));

            exchangeRateService.updateExchangeRates(purchase);

            verify(exchangeRateRepository, times(1)).saveAll(any());
            verify(exchangeRateRepository, times(1))
                    .notExistsByCountryCurrencyAndEffectiveDate(anyString(), any(LocalDate.class));
            verify(metricsHelper, times(1)).registryFiscalServiceRetrievalElapsedTime(anyLong());
        }
    }

    @Test
    void givenValidPurchase_whenCallUpdateExchangeRatesAndNoNewConversionRate_thenDontSaveAll() throws Exception {
        final var purchase = PurchaseJpaEntity.newPurchase("Description", LocalDate.now(), BigDecimal.valueOf(100));
        final var conversionRate = ConversionRate.with(
                BigDecimal.valueOf(5.22),
                LocalDate.of(2023, 10, 30),
                "Brazil-Real");

        when(response.statusCode()).thenReturn(HttpStatus.OK.value());
        when(response.body()).thenReturn("json response");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandlers.ofString().getClass())))
                .thenReturn(response);
        when(exchangeRateRepository.notExistsByCountryCurrencyAndEffectiveDate(
                conversionRate.countryCurrency(),
                conversionRate.effectiveDate()))
                .thenReturn(false);

        try (MockedStatic<JsonUtils> mockedJsonUtils = Mockito.mockStatic(JsonUtils.class)) {
            mockedJsonUtils.when(() -> JsonUtils.extractDataList(anyString(), any())).thenReturn(List.of(conversionRate));

            exchangeRateService.updateExchangeRates(purchase);

            verify(exchangeRateRepository, times(1))
                    .notExistsByCountryCurrencyAndEffectiveDate(anyString(), any(LocalDate.class));
            verify(exchangeRateRepository, never()).saveAll(any());
            verify(metricsHelper, times(1)).registryFiscalServiceRetrievalElapsedTime(anyLong());
        }
    }

    @Test
    void givenValidPurchase_whenCallUpdateExchangeRatesAndServiceUnavailable_thenThrowsRetryException() throws Exception {
        final var purchase = PurchaseJpaEntity.newPurchase("Description", LocalDate.now(), BigDecimal.valueOf(100));
        final var expectedExceptionMessage = "Unexpected response from API";

        when(response.statusCode()).thenReturn(HttpStatus.SERVICE_UNAVAILABLE.value());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandlers.ofString().getClass())))
                .thenReturn(response);

        final var actualException =
                Assertions.assertThrows(RetryableException.class, () -> exchangeRateService.updateExchangeRates(purchase));

        Assertions.assertEquals(expectedExceptionMessage, actualException.getMessage());
        verify(exchangeRateRepository, never()).saveAll(any());
        verify(exchangeRateRepository, never()).saveAll(any());
        verify(metricsHelper, times(1)).registryFiscalServiceRetrievalElapsedTime(anyLong());
    }

    @Test
    void givenValidPurchase_whenCallUpdateExchangeRatesAndIoException_thenThrowsRetryException() throws Exception {
        final var purchase = PurchaseJpaEntity.newPurchase("Description", LocalDate.now(), BigDecimal.valueOf(100));
        final var expectedExceptionMessage = "Error processing request";

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandlers.ofString().getClass())))
                .thenThrow(IOException.class);

        final var actualException =
                Assertions.assertThrows(RetryableException.class, () -> exchangeRateService.updateExchangeRates(purchase));

        Assertions.assertEquals(expectedExceptionMessage, actualException.getMessage());
        verify(exchangeRateRepository, never()).saveAll(any());
        verify(metricsHelper, times(1)).incrementRequestErrorMetric();
        verify(metricsHelper, never()).registryFiscalServiceRetrievalElapsedTime(anyLong());
    }

    @Test
    void givenValidPurchase_whenCallUpdateExchangeRates_thenTheUpstreamQueryPinsFieldsFilterSortAndPageSize()
            throws Exception {
        final var purchase = PurchaseJpaEntity.newPurchase(
                "Description", LocalDate.of(2024, 7, 15), BigDecimal.valueOf(100));

        when(response.statusCode()).thenReturn(HttpStatus.SERVICE_UNAVAILABLE.value());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandlers.ofString().getClass())))
                .thenReturn(response);

        Assertions.assertThrows(RetryableException.class, () -> exchangeRateService.updateExchangeRates(purchase));

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
        final var purchase = PurchaseJpaEntity.newPurchase("Description", LocalDate.now(), BigDecimal.valueOf(100));
        final var effectiveDate = LocalDate.of(2023, 10, 30);
        final var first = ConversionRate.with(BigDecimal.valueOf(5.22), effectiveDate, "Brazil-Real");
        final var duplicate = ConversionRate.with(BigDecimal.valueOf(9.99), effectiveDate, "Brazil-Real");

        when(response.statusCode()).thenReturn(HttpStatus.OK.value());
        when(response.body()).thenReturn("json response");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandlers.ofString().getClass())))
                .thenReturn(response);
        when(exchangeRateRepository.notExistsByCountryCurrencyAndEffectiveDate("Brazil-Real", effectiveDate))
                .thenReturn(true);

        try (MockedStatic<JsonUtils> mockedJsonUtils = Mockito.mockStatic(JsonUtils.class)) {
            mockedJsonUtils.when(() -> JsonUtils.extractDataList(anyString(), any()))
                    .thenReturn(List.of(first, duplicate));

            exchangeRateService.updateExchangeRates(purchase);

            final var saved = ArgumentCaptor.forClass(List.class);
            verify(exchangeRateRepository).saveAll(saved.capture());
            Assertions.assertEquals(1, saved.getValue().size());
            final var kept = (ExchangeRateJpaEntity) saved.getValue().get(0);
            Assertions.assertEquals(BigDecimal.valueOf(5.22), kept.getRateValue());
        }
    }

    @Test
    void givenAnActiveRetryContext_whenCallUpdateExchangeRatesFails_thenTheAttemptIsReportedWithoutMaskingTheFailure()
            throws Exception {
        final var purchase = PurchaseJpaEntity.newPurchase("Description", LocalDate.now(), BigDecimal.valueOf(100));
        final var retryContext = new RetryContextSupport(null);
        retryContext.registerThrowable(new RetryableException("previous attempt"));
        RetrySynchronizationManager.register(retryContext);

        try {
            when(response.statusCode()).thenReturn(HttpStatus.SERVICE_UNAVAILABLE.value());
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandlers.ofString().getClass())))
                    .thenReturn(response);

            final var actualException = Assertions.assertThrows(
                    RetryableException.class, () -> exchangeRateService.updateExchangeRates(purchase));

            Assertions.assertEquals("Unexpected response from API", actualException.getMessage());
            Assertions.assertEquals(1, retryContext.getRetryCount());
        } finally {
            RetrySynchronizationManager.clear();
        }
    }
}
