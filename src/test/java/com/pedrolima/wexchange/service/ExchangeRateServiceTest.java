package com.pedrolima.wexchange.service;

import com.pedrolima.wexchange.entities.PurchaseJpaEntity;
import com.pedrolima.wexchange.integration.fiscal.bean.ConversionRate;
import com.pedrolima.wexchange.repositories.ConversionRateRepository;
import com.pedrolima.wexchange.services.async.ExchangeRateService;
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

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ExchangeRateServiceTest {

    @Mock
    private ConversionRateRepository conversionRateRepository;

    @Mock
    private MetricsHelper metricsHelper;

    @Mock
    private HttpClient httpClient;

    @InjectMocks
    private ExchangeRateService exchangeRateService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(exchangeRateService, "exchangeApiUrl", "http://mocked.api.url");
    }

    @Test
    void givenValidPurchase_whenCallUpdateExchangeRates_thenUpdateExchangeRates() throws Exception {
        final var purchase = PurchaseJpaEntity.newPurchase("Description", LocalDate.now(), BigDecimal.valueOf(100));
        final var conversionRate = ConversionRate.with(
                BigDecimal.valueOf(5.22),
                LocalDate.of(2023, 10, 30),
                "Brazil-Real");

        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(HttpStatus.OK.value());
        when(response.body()).thenReturn("json response");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandlers.ofString().getClass())))
                .thenReturn(response);
        when(conversionRateRepository.notExistsByCountryCurrencyAndEffectiveDate(
                conversionRate.countryCurrency(),
                conversionRate.effectiveDate()))
                .thenReturn(true);

        try (MockedStatic<JsonUtils> mockedJsonUtils = Mockito.mockStatic(JsonUtils.class)) {
            mockedJsonUtils.when(() -> JsonUtils.extractDataList(anyString(), any())).thenReturn(List.of(conversionRate));

            exchangeRateService.updateExchangeRates(purchase);

            verify(conversionRateRepository, times(1)).saveAll(any());
        }
    }
}
