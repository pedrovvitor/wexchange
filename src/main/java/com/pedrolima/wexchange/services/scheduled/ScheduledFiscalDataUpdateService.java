package com.pedrolima.wexchange.services.scheduled;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.pedrolima.wexchange.entities.CountryCurrencyJpaEntity;
import com.pedrolima.wexchange.exceptions.RetryableException;
import com.pedrolima.wexchange.integration.fiscal.bean.CountryCurrency;
import com.pedrolima.wexchange.integration.fiscal.builder.ApiUrlBuilder;
import com.pedrolima.wexchange.repositories.CountryCurrencyRepository;
import com.pedrolima.wexchange.utils.JsonUtils;
import com.pedrolima.wexchange.utils.MetricsHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.Collectors;

import static com.pedrolima.wexchange.integration.fiscal.builder.ApiUrlBuilder.FieldType.COUNTRY;
import static com.pedrolima.wexchange.integration.fiscal.builder.ApiUrlBuilder.FieldType.COUNTRY_CURRENCY;
import static com.pedrolima.wexchange.integration.fiscal.builder.ApiUrlBuilder.FieldType.CURRENCY;
import static com.pedrolima.wexchange.integration.fiscal.builder.ApiUrlBuilder.PAGE_SIZE_MAX_VALUE;
import static com.pedrolima.wexchange.integration.fiscal.builder.ApiUrlBuilder.PageType.SIZE;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledFiscalDataUpdateService {

    public static final int ONE_DAY_MS = 86400000;

    private final CountryCurrencyRepository repository;

    @Value("${fiscal.service.api.endpoint}")
    private String exchangeApiUrl;

    private final MetricsHelper metricsHelper;

    /*
        Maintain this task in this service would imply in scalability issues.
        This could be done in many different ways. eg: a separated microservice or an AWS lambda
        In this mocked system, I would use localstack to provide the tools to make it locally doable.
     */
    @Retryable(
            retryFor = {RetryableException.class},
            backoff = @Backoff(delay = 10_000),
            maxAttempts = 10
    )
    @Scheduled(fixedRate = ONE_DAY_MS, initialDelay = 1)
    public void updateAllExchangeRates() {
        String fullUrl = buildFullUrl();
        HttpRequest request = buildHttpRequest(fullUrl);

        try {
            StopWatch watch = new StopWatch();

            HttpResponse<String> response = sendRequest(request);
            if (response.statusCode() == HttpStatus.OK.value()) {
                watch.start();
                List<CountryCurrency> countryCurrencies = JsonUtils.extractDataList(response.body(), CountryCurrency.class);

                saveCountryCurrencies(countryCurrencies);
                watch.stop();
                log.debug("Processing and saving all Country Currencies successfully. Elapsed time {}",
                        watch.formatTime());
            } else {
                log.warn("Unexpected response status: {} - Retry attempt: {}", response.statusCode(),
                        RetrySynchronizationManager.getContext().getRetryCount() + 1);
                throw new RetryableException("Unexpected response from API");
            }
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON response: {} - Retry attempt: {}", e,
                    RetrySynchronizationManager.getContext().getRetryCount() + 1);
            metricsHelper.incrementParsingErrorMetric();
            throw new RetryableException("Error parsing API response", e);
        } catch (IOException | InterruptedException e) {
            log.error("Failed to communicate with fiscaldata api: {} - Retry attempt: {}", e,
                    RetrySynchronizationManager.getContext().getRetryCount() + 1);
            throw new RetryableException("Failed to communicate with fiscaldata api", e);
        }
    }

    private void saveCountryCurrencies(final List<CountryCurrency> countryCurrencies) {
        final var fiscalDataApiCountryCurrencies =
                countryCurrencies.stream()
                        .filter(countryCurrency -> repository.notExistsByCountryCurrency(countryCurrency.countryCurrency()))
                        .map(CountryCurrencyJpaEntity::with)
                        .collect(Collectors.toList());

        repository.saveAll(fiscalDataApiCountryCurrencies);
    }

    private HttpResponse<String> sendRequest(HttpRequest request) throws IOException, InterruptedException {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        stopWatch.stop();
        metricsHelper.registryFiscalServiceRetrievalElapsedTime(stopWatch.getNanoTime());
        return response;
    }

    private String buildFullUrl() {
        return new ApiUrlBuilder(exchangeApiUrl)
                .addFields(COUNTRY_CURRENCY, CURRENCY, COUNTRY)
                .addPagination(SIZE, PAGE_SIZE_MAX_VALUE)
                .build();
    }

    private HttpRequest buildHttpRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
    }
}
