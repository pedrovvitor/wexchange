package com.pedrolima.wexchange.usecases.purchase.convert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedrolima.wexchange.api.purchase.ConvertPurchaseApiInput;
import com.pedrolima.wexchange.api.purchase.ConvertPurchaseApiOutput;
import com.pedrolima.wexchange.bean.exchange.ExchangeData;
import com.pedrolima.wexchange.bean.exchange.FiscalDataApiResponse;
import com.pedrolima.wexchange.entities.Purchase;
import com.pedrolima.wexchange.exception.ExchangeRateNotFoundException;
import com.pedrolima.wexchange.exception.PurchaseConversionException;
import com.pedrolima.wexchange.exception.ResourceNotFoundException;
import com.pedrolima.wexchange.exception.RetryableException;
import com.pedrolima.wexchange.integration.fiscal.ApiUrlBuilder;
import com.pedrolima.wexchange.repositories.PurchaseRepository;
import com.pedrolima.wexchange.util.CountryCurrencyUtils;
import com.pedrolima.wexchange.util.MetricsHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.pedrolima.wexchange.integration.fiscal.ApiUrlBuilder.FieldType.COUNTRY;
import static com.pedrolima.wexchange.integration.fiscal.ApiUrlBuilder.FieldType.COUNTRY_CURRENCY;
import static com.pedrolima.wexchange.integration.fiscal.ApiUrlBuilder.FieldType.CURRENCY;
import static com.pedrolima.wexchange.integration.fiscal.ApiUrlBuilder.FieldType.EFFECTIVE_DATE;
import static com.pedrolima.wexchange.integration.fiscal.ApiUrlBuilder.FieldType.EXCHANGE_RATE;
import static com.pedrolima.wexchange.integration.fiscal.ApiUrlBuilder.ParamComparator;
import static com.pedrolima.wexchange.integration.fiscal.ApiUrlBuilder.SortOrder;

@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultConvertPurchaseUseCase extends ConvertPurchaseUseCase {

    @Value("${fiscal.service.api.endpoint}")
    private String exchangeApiUrl;

    private final PurchaseRepository purchaseRepository;

    private final ObjectMapper mapper;

    private final MetricsHelper metricsHelper;

    @Override
    @Retryable(
            retryFor = {RetryableException.class},
            backoff = @Backoff(delay = 1000))
    public ConvertPurchaseApiOutput execute(final ConvertPurchaseApiInput input) {
        /*
            Given that the fiscal api has registers in capitalized first letter and all capitalized depending on date
            I opted to provide both conditions
         */
        final var formattedCountryCurrency =
                CountryCurrencyUtils.formatCountryCurrency(input.countryCurrency());
        final Purchase purchase = fetchPurchase(input);
        final String fullUrl = buildFullUrl(formattedCountryCurrency, purchase);
        final HttpRequest request = buildHttpRequest(fullUrl);

        try {
            HttpResponse<String> response = sendRequest(request);
            return processResponse(response, purchase, formattedCountryCurrency);
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON response: {}", e.toString());
            metricsHelper.incrementParsingErrorMetric();
            throw new PurchaseConversionException("Error parsing API response");
        } catch (IOException | InterruptedException e) {
            log.error("Failed to communicate with fiscaldata api: {}", e.toString());
            throw new RetryableException("Failed to communicate with fiscaldata api");
        }
    }

    private Purchase fetchPurchase(ConvertPurchaseApiInput input) {
        return purchaseRepository.findById(input.purchaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found for id: " + input.purchaseId()));
    }

    private String buildFullUrl(String formattedCountryCurrency, Purchase purchase) {

        final var conversionPeriod = calculateConversionAvailablePeriod(purchase);

        return new ApiUrlBuilder(exchangeApiUrl)
                .addFields(EXCHANGE_RATE, EFFECTIVE_DATE, COUNTRY, CURRENCY)
                .addFilter(EFFECTIVE_DATE, ParamComparator.GTE, conversionPeriod.getLeft())
                .addFilter(EFFECTIVE_DATE, ParamComparator.LTE, conversionPeriod.getRight())
                .addFilter(COUNTRY_CURRENCY, ParamComparator.IN, formattedCountryCurrency)
                .addSorting(SortOrder.DESC, EFFECTIVE_DATE)
                .build();
    }

    private Pair<String, String> calculateConversionAvailablePeriod(final Purchase purchase) {
        final var sixMonthsBefore = purchase.getDate().minusMonths(6).toString();
        return Pair.of(sixMonthsBefore, purchase.getDate().toString());
    }

    private HttpRequest buildHttpRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
    }

    private HttpResponse<String> sendRequest(HttpRequest request) throws IOException, InterruptedException {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        stopWatch.stop();
        metricsHelper.registryExchangeRateRetrievalElapsedTime(stopWatch.getNanoTime());
        return response;
    }

    private ConvertPurchaseApiOutput processResponse(
            final HttpResponse<String> response,
            final Purchase purchase,
            final String formattedCountryCurrency
    ) throws JsonProcessingException {
        if (response.statusCode() == HttpStatus.OK.value()) {
            return parseResponseAndCalculate(response, purchase, formattedCountryCurrency);
        } else {
            log.error("Unexpected response status: {}. {}", response.statusCode(), response);
            throw new ResponseStatusException(HttpStatus.valueOf(response.statusCode()), "Unexpected response from API");
        }
    }

    private ConvertPurchaseApiOutput parseResponseAndCalculate(
            final HttpResponse<String> response,
            final Purchase purchase,
            final String formattedCountryCurrency
    ) throws JsonProcessingException {
        final FiscalDataApiResponse fiscalDataApiResponse = mapper.readValue(response.body(),
                FiscalDataApiResponse.class);
        log.debug("Fiscal service api call returned with {} exchange rates", fiscalDataApiResponse.data().size());
        final var mostRecentExchangeData = extractMostRecentRegister(fiscalDataApiResponse, purchase, formattedCountryCurrency);

        final var convertedAmount = calculateConvertedAmount(purchase, mostRecentExchangeData.exchangeRate());

        return ConvertPurchaseApiOutput.with(
                purchase.getId(),
                purchase.getDescription(),
                purchase.getDate().toString(),
                purchase.getAmount(),
                mostRecentExchangeData.country(),
                mostRecentExchangeData.currency(),
                mostRecentExchangeData.exchangeRate(),
                mostRecentExchangeData.effectiveDate(),
                convertedAmount
        );
    }

    private ExchangeData extractMostRecentRegister(
            final FiscalDataApiResponse response,
            final Purchase purchase,
            final String formattedCountryCurrency
    ) {
        final var availablePeriod = calculateConversionAvailablePeriod(purchase);
        return response.data().stream()
                .findFirst()
                .orElseThrow(() ->
                        new ExchangeRateNotFoundException(
                                "Exchange rate not found for currencies {%s} on period %s-%s"
                                        .formatted(
                                                formattedCountryCurrency,
                                                availablePeriod.getLeft(),
                                                availablePeriod.getRight()
                                        )
                        )
                );
    }

    private BigDecimal calculateConvertedAmount(Purchase purchase, BigDecimal exchangeRate) {
        return purchase.getAmount().multiply(exchangeRate)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
