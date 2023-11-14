package com.pedrolima.wexchange.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedrolima.wexchange.api.ApiLink;
import com.pedrolima.wexchange.bean.exchange.CountryCurrencyOutput;
import com.pedrolima.wexchange.bean.exchange.CountryCurrencyResponse;
import com.pedrolima.wexchange.exception.PurchaseConversionException;
import com.pedrolima.wexchange.exception.RetryableException;
import com.pedrolima.wexchange.integration.fiscal.ApiUrlBuilder;
import com.pedrolima.wexchange.util.MetricsHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.pedrolima.wexchange.integration.fiscal.ApiUrlBuilder.FieldType.COUNTRY;
import static com.pedrolima.wexchange.integration.fiscal.ApiUrlBuilder.FieldType.COUNTRY_CURRENCY;
import static com.pedrolima.wexchange.integration.fiscal.ApiUrlBuilder.FieldType.CURRENCY;

@Service
@Slf4j
@RequiredArgsConstructor
public class CurrenciesService {

    private static final int PAGE_SIZE_MAX_VALUE = 10000;

    @Value("${fiscal.service.api.endpoint}")
    private String exchangeApiUrl;

    private final ObjectMapper mapper;

    private final MetricsHelper metricsHelper;

    @Retryable(
            retryFor = {IOException.class, InterruptedException.class},
            backoff = @Backoff(delay = 1000))
    public CountryCurrencyOutput getAllExchangeRates() {
        String fullUrl = buildFullUrl();
        HttpRequest request = buildHttpRequest(fullUrl);

        try {
            HttpResponse<String> response = sendRequest(request);
            if (response.statusCode() == HttpStatus.OK.value()) {
                CountryCurrencyResponse exchangeRateResponse = mapper.readValue(response.body(), CountryCurrencyResponse.class);

                final var convertParams = Map.of(
                        "{id}", "String: Purchase id (UUID format)",
                        "country_currency", "String: Country-Currency to convert"
                );

                final var relatedLinks = List.of(
                        ApiLink.with("convert", "/v1/purchases/{id}/convert", "GET", convertParams)
                );

                return new CountryCurrencyOutput(exchangeRateResponse.data(), relatedLinks);
            } else {
                log.warn("Unexpected response status: {}", response.statusCode());
                throw new ResponseStatusException(HttpStatus.valueOf(response.statusCode()), "Unexpected response from API");
            }
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON response: {}", e.toString());
            metricsHelper.incrementParsingErrorMetric();
            throw new PurchaseConversionException("Error parsing API response");
        } catch (IOException | InterruptedException e) {
            log.error("Failed to communicate with fiscaldata api: {}", e.toString());
            throw new RetryableException("Failed to communicate with fiscaldata api");
        }
    }

    private HttpResponse<String> sendRequest(HttpRequest request) throws IOException, InterruptedException {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        stopWatch.stop();
        metricsHelper.registryExchangeRateRetrievalElapsedTime(stopWatch.getNanoTime());
        return response;
    }

    private String buildFullUrl() {
        return new ApiUrlBuilder(exchangeApiUrl)
                .addFields(COUNTRY_CURRENCY, CURRENCY, COUNTRY)
                .addPagination(ApiUrlBuilder.PageType.SIZE, PAGE_SIZE_MAX_VALUE)
                .build();
    }

    private HttpRequest buildHttpRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
    }
}
