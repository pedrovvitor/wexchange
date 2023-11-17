package com.pedrolima.wexchange.service;

import com.pedrolima.wexchange.exceptions.RetryableException;
import com.pedrolima.wexchange.integration.fiscal.beans.CountryCurrencyInput;
import com.pedrolima.wexchange.repositories.CountryCurrencyRepository;
import com.pedrolima.wexchange.services.scheduled.CountryCurrencyUpdaterService;
import com.pedrolima.wexchange.utils.JsonUtils;
import com.pedrolima.wexchange.utils.MetricsHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CountryCurrencyUpdaterServiceTest {

    @Mock
    private CountryCurrencyRepository repository;

    @Mock
    private MetricsHelper metricsHelper;

    @Mock
    private HttpClient httpClient;

    @InjectMocks
    private CountryCurrencyUpdaterService countryCurrencyUpdaterService;

    private HttpResponse<String> response;

    @BeforeEach
    void setUp() {
        response = Mockito.mock(HttpResponse.class);
        ReflectionTestUtils.setField(countryCurrencyUpdaterService, "exchangeApiUrl", "http://mocked.api.url");
    }

    @Test
    void whenUpdateAllExchangeRates_andApiResponseIsOk_thenSaveCountryCurrencies() throws IOException, InterruptedException {
        final var aCountryCurrencyInput =
                new CountryCurrencyInput("Brazil-Real", "Brazil", "Real");
        when(response.statusCode()).thenReturn(HttpStatus.OK.value());
        when(response.body()).thenReturn("json response");
        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandlers.ofString().getClass())))
                .thenReturn(response);

        try (MockedStatic<JsonUtils> jsonUtilsMockedStatic = Mockito.mockStatic(JsonUtils.class)) {
            jsonUtilsMockedStatic.when(() -> JsonUtils.extractDataList(anyString(), any()))
                    .thenReturn(List.of(aCountryCurrencyInput));

            countryCurrencyUpdaterService.synchronizeCountryCurrencies();

            verify(repository, times(1)).saveAll(any());
            verify(metricsHelper, never()).incrementParsingErrorMetric();
            verify(metricsHelper, times(1)).registryFiscalServiceRetrievalElapsedTime(anyLong());
        }
    }

    @Test
    void whenUpdateAllExchangeRates_andApiResponseIsNotOk_thenThrowRetryableException() throws IOException, InterruptedException {
        when(response.statusCode()).thenReturn(HttpStatus.SERVICE_UNAVAILABLE.value());
        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandlers.ofString().getClass())))
                .thenReturn(response);

        assertThrows(RetryableException.class, () -> countryCurrencyUpdaterService.synchronizeCountryCurrencies());
        verify(repository, never()).saveAll(any());
        verify(metricsHelper, never()).incrementParsingErrorMetric();
        verify(metricsHelper, times(1)).registryFiscalServiceRetrievalElapsedTime(anyLong());
    }

    @Test
    void whenUpdateAllExchangeRates_andIOExceptionOccurs_thenThrowRetryableException() throws IOException, InterruptedException {
        when(httpClient.send(any(HttpRequest.class), any())).thenThrow(IOException.class);

        assertThrows(RetryableException.class, () -> countryCurrencyUpdaterService.synchronizeCountryCurrencies());
        verify(repository, never()).saveAll(any());
        verify(metricsHelper, never()).incrementParsingErrorMetric();
    }
}
