package com.pedrolima.wexchange.adapter.out.fiscal;

import com.pedrolima.wexchange.adapter.out.persistence.ExchangeRateJpaEntity;
import com.pedrolima.wexchange.adapter.out.persistence.ExchangeRateRepository;
import com.pedrolima.wexchange.application.port.ExchangeRateRefresher;
import com.pedrolima.wexchange.domain.error.RetryableException;
import com.pedrolima.wexchange.domain.exchange.ConversionWindow;
import com.pedrolima.wexchange.domain.purchase.Purchase;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.FieldType.COUNTRY_CURRENCY;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.FieldType.EFFECTIVE_DATE;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.FieldType.EXCHANGE_RATE;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.PAGE_SIZE_MAX_VALUE;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.PageType.SIZE;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.ParamComparator.GTE;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.ParamComparator.LTE;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.SortOrder.DESC;
import static com.pedrolima.wexchange.adapter.out.fiscal.HttpRequestUtils.buildHttpRequest;

/**
 * Service for updating exchange rates in response to new purchase registrations.
 * This service operates asynchronously to interact with an external fiscal service API.
 * Upon the registration of a new purchase, it retrieves the latest exchange rates for all available
 * country currencies and updates the database accordingly.
 * <p>
 * The update process involves the following steps:
 * 1. Check if the exchange rates for the purchase date already exist in the database.
 * If they do, the update process is skipped to avoid duplication.
 * 2. Build a request to the fiscal service API, targeting all existing exchange rates/country currency
 * pairs within the past six months relative to the purchase date.
 * 3. Send the request and process the response, extracting and filtering the conversion rates.
 * 4. Update the database with new and relevant exchange rate information.
 * <p>
 * This service uses asynchronous processing to enhance performance and ensure that the API call
 * does not block the main application flow. In case of failures, the operation is retried with
 * a specified backoff strategy.
 */

@Service
@Slf4j
public class ExchangeRateService implements ExchangeRateRefresher {

    private final String exchangeApiUrl;

    private final ExchangeRateRepository exchangeRateRepository;

    private final MetricsHelper metricsHelper;

    private final HttpClient httpClient;

    public ExchangeRateService(
            @Value("${fiscal.service.api.endpoint}") final String exchangeApiUrl,
            final ExchangeRateRepository exchangeRateRepository,
            final MetricsHelper metricsHelper,
            final HttpClient httpClient
    ) {
        this.exchangeApiUrl = exchangeApiUrl;
        this.exchangeRateRepository = exchangeRateRepository;
        this.metricsHelper = metricsHelper;
        this.httpClient = httpClient;
    }

    @Override
    @Async
    @Retryable(retryFor = {RetryableException.class}, backoff = @Backoff(delay = 2000), maxAttempts = 5)
    public void refreshFor(final Purchase purchase) {

        final var apiUri = buildUri(purchase);
        final var request = buildHttpRequest(apiUri);

        try {
            final var response = sendRequest(request);

            if (response.statusCode() == HttpStatus.OK.value()) {
                final var conversionRateJpaEntities = processAndFilterConversionRate(response);

                if (!conversionRateJpaEntities.isEmpty()) {
                    exchangeRateRepository.saveAll(conversionRateJpaEntities);
                }
            } else {
                log.warn("Unexpected response status: {} - Retry attempt: {}", response.statusCode(), getRetryCount());
                throw new RetryableException("Unexpected response from API");
            }
        } catch (IOException | InterruptedException e) {
            metricsHelper.incrementRequestErrorMetric();
            log.error("Error occurred: {} - Retry attempt: {}", e, getRetryCount());
            throw new RetryableException("Error processing request");
        }
    }

    private List<ExchangeRateJpaEntity> processAndFilterConversionRate(final HttpResponse<String> response) throws IOException {
        final var conversionRates = JsonUtils.extractDataList(response.body(), ConversionRate.class);
        return conversionRates.stream()
                .map(rate -> ExchangeRateJpaEntity.newConversionRate(
                        rate.countryCurrency(), rate.effectiveDate(), rate.exchangeRate()))
                .collect(Collectors.toMap(
                        ExchangeRateJpaEntity::getCountryCurrency,
                        Function.identity(),
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .filter(this::isConversionRateNew)
                .collect(Collectors.toList());
    }

    private HttpResponse<String> sendRequest(final HttpRequest request) throws IOException, InterruptedException {
        final var watch = new StopWatch();
        watch.start();
        final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        watch.stop();
        metricsHelper.registryFiscalServiceRetrievalElapsedTime(watch.getTime());
        return response;
    }

    private String buildUri(final Purchase purchase) {
        final ConversionWindow conversionPeriod = purchase.conversionWindow();

        return new ApiUrlBuilder(exchangeApiUrl)
                .addFields(EXCHANGE_RATE, EFFECTIVE_DATE, COUNTRY_CURRENCY)
                .addFilter(EFFECTIVE_DATE, GTE, conversionPeriod.start().toString())
                .addFilter(EFFECTIVE_DATE, LTE, conversionPeriod.end().toString())
                .addSorting(DESC, EFFECTIVE_DATE)
                .addSorting(DESC, COUNTRY_CURRENCY)
                .addPagination(SIZE, PAGE_SIZE_MAX_VALUE)
                .build();
    }

    private boolean isConversionRateNew(final ExchangeRateJpaEntity conversionRate) {
        return exchangeRateRepository.notExistsByCountryCurrencyAndEffectiveDate(
                conversionRate.getCountryCurrency(),
                conversionRate.getEffectiveDate()
        );
    }

    private int getRetryCount() {
        final var context = RetrySynchronizationManager.getContext();
        return (context != null) ? context.getRetryCount() : 0;
    }
}
