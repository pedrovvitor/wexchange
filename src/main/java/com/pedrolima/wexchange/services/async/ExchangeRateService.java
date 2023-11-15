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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

    @Value("${fiscal.service.api.endpoint}")
    private String exchangeApiUrl;

    private final ConversionRateRepository repository;

    private final MetricsHelper metricsHelper;

    @Async
    @Retryable(retryFor = {RetryableException.class}, backoff = @Backoff(delay = 2000))
    public void updateExchangeRates(PurchaseJpaEntity purchase) {
        final var watch = new StopWatch();
        final var apiUri = buildUri(purchase);
        final var request = buildHttpRequest(apiUri);

        try {
            watch.start();
            final var response = sendRequest(request);
            watch.stop();
            metricsHelper.registryFiscalServiceRetrievalElapsedTime(watch.getTime());
            final var conversionRates = JsonUtils.extractDataList(response.body(), ConversionRate.class);
            final var conversionRateMap = new LinkedHashMap<String, ConversionRateJpaEntity>();
            conversionRates.stream()
                    .map(ConversionRateJpaEntity::with)
                    .filter(conversionRate -> Objects.nonNull(conversionRate.getCountryCurrency()))
                    .forEach(jpaEntity -> conversionRateMap.putIfAbsent(jpaEntity.getCountryCurrency(), jpaEntity));

            List<ConversionRateJpaEntity> ratesToSave = conversionRateMap.values().stream()
                    .filter(value -> repository.notExistsByCountryCurrencyAndEffectiveDate(value.getCountryCurrency(),
                            value.getEffectiveDate()))
                    .collect(Collectors.toList());

            if (!ratesToSave.isEmpty()) {
                repository.saveAll(ratesToSave);
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error occurred: {}", e.toString());
            throw new RetryableException("Error processing request.", e);
        }
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
}
