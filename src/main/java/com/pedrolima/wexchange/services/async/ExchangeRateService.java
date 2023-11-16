package com.pedrolima.wexchange.services.async;

import com.pedrolima.wexchange.entities.ConversionRateJpaEntity;
import com.pedrolima.wexchange.entities.PurchaseJpaEntity;
import com.pedrolima.wexchange.exceptions.RetryableException;
import com.pedrolima.wexchange.integration.fiscal.bean.ConversionRate;
import com.pedrolima.wexchange.integration.fiscal.builder.ApiUrlBuilder;
import com.pedrolima.wexchange.repositories.ConversionRateRepository;
import com.pedrolima.wexchange.utils.JsonUtils;
import com.pedrolima.wexchange.utils.MetricsHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.pedrolima.wexchange.integration.fiscal.builder.ApiUrlBuilder.FieldType.COUNTRY_CURRENCY;
import static com.pedrolima.wexchange.integration.fiscal.builder.ApiUrlBuilder.FieldType.EFFECTIVE_DATE;
import static com.pedrolima.wexchange.integration.fiscal.builder.ApiUrlBuilder.FieldType.EXCHANGE_RATE;
import static com.pedrolima.wexchange.integration.fiscal.builder.ApiUrlBuilder.PAGE_SIZE_MAX_VALUE;
import static com.pedrolima.wexchange.integration.fiscal.builder.ApiUrlBuilder.PageType.SIZE;
import static com.pedrolima.wexchange.integration.fiscal.builder.ApiUrlBuilder.ParamComparator.GTE;
import static com.pedrolima.wexchange.integration.fiscal.builder.ApiUrlBuilder.ParamComparator.LTE;
import static com.pedrolima.wexchange.integration.fiscal.builder.ApiUrlBuilder.SortOrder.DESC;
import static com.pedrolima.wexchange.utils.ConversionUtils.calculateConversionAvailablePeriod;
import static com.pedrolima.wexchange.utils.HttpRequestUtils.buildHttpRequest;
import static com.pedrolima.wexchange.utils.HttpRequestUtils.sendRequest;

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
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

    @Value("${fiscal.service.api.endpoint}")
    private String exchangeApiUrl;

    private final ConversionRateRepository conversionRateRepository;

    private final MetricsHelper metricsHelper;

    @Async
    @Retryable(retryFor = {RetryableException.class}, backoff = @Backoff(delay = 2000), maxAttempts = 5)
    public void updateExchangeRates(PurchaseJpaEntity purchase) {

        final var apiUri = buildUri(purchase);
        final var request = buildHttpRequest(apiUri);

        try {
            final var response = sendRequestWithMetrics(request);
            final var conversionRateJpaEntities = processAndFilterConversionRate(response);

            if (!conversionRateJpaEntities.isEmpty()) {
                conversionRateRepository.saveAll(conversionRateJpaEntities);
            }
        } catch (IOException | InterruptedException e) {
            metricsHelper.incrementRequestErrorMetric();
            log.error("Error occurred: {}", e.toString());
            throw new RetryableException("Error processing request.", e);
        }
    }

    private List<ConversionRateJpaEntity> processAndFilterConversionRate(final HttpResponse<String> response) throws IOException {
        final var conversionRates = JsonUtils.extractDataList(response.body(), ConversionRate.class);
        return conversionRates.stream()
                .map(ConversionRateJpaEntity::with)
                .collect(Collectors.toMap(
                        ConversionRateJpaEntity::getCountryCurrency,
                        Function.identity(),
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .filter(this::isConversionRateNew)
                .collect(Collectors.toList());
    }

    private HttpResponse<String> sendRequestWithMetrics(final HttpRequest request) throws IOException, InterruptedException {
        final var watch = new StopWatch();
        watch.start();
        final var response = sendRequest(request);
        watch.stop();
        metricsHelper.registryFiscalServiceRetrievalElapsedTime(watch.getTime());
        return response;
    }

    private String buildUri(final PurchaseJpaEntity purchase) {
        final var conversionPeriod = calculateConversionAvailablePeriod(purchase);

        return new ApiUrlBuilder(exchangeApiUrl)
                .addFields(EXCHANGE_RATE, EFFECTIVE_DATE, COUNTRY_CURRENCY)
                .addFilter(EFFECTIVE_DATE, GTE, conversionPeriod.getLeft().toString())
                .addFilter(EFFECTIVE_DATE, LTE, conversionPeriod.getRight().toString())
                .addSorting(DESC, EFFECTIVE_DATE)
                .addSorting(DESC, COUNTRY_CURRENCY)
                .addPagination(SIZE, PAGE_SIZE_MAX_VALUE)
                .build();
    }

    private boolean isConversionRateNew(final ConversionRateJpaEntity conversionRate) {
        return conversionRateRepository.notExistsByCountryCurrencyAndEffectiveDate(
                conversionRate.getCountryCurrency(),
                conversionRate.getEffectiveDate()
        );
    }
}
