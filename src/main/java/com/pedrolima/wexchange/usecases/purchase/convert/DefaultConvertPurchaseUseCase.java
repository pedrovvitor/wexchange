package com.pedrolima.wexchange.usecases.purchase.convert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedrolima.wexchange.api.purchase.ConvertPurchaseApiInput;
import com.pedrolima.wexchange.api.purchase.ConvertPurchaseApiOutput;
import com.pedrolima.wexchange.bean.exchange.ExchangeRateData;
import com.pedrolima.wexchange.bean.exchange.ExchangeRateResponse;
import com.pedrolima.wexchange.entities.Purchase;
import com.pedrolima.wexchange.exception.ExchangeRateNotFoundException;
import com.pedrolima.wexchange.exception.PurchaseConversionException;
import com.pedrolima.wexchange.exception.ResourceNotFoundException;
import com.pedrolima.wexchange.exception.RetryableException;
import com.pedrolima.wexchange.integration.fiscal.ApiUrlBuilder;
import com.pedrolima.wexchange.repositories.PurchaseRepository;
import com.pedrolima.wexchange.util.MetricsUtils;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.pedrolima.wexchange.integration.fiscal.ApiUrlBuilder.FieldType.COUNTRY_CURRENCY;
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

    private final MetricsUtils metricsUtils;

    @Override
    @Retryable(
            retryFor = {RetryableException.class},
            backoff = @Backoff(delay = 1000))
    public ConvertPurchaseApiOutput execute(final ConvertPurchaseApiInput convertPurchaseApiInput) {
        final Purchase purchase = fetchPurchase(convertPurchaseApiInput);
        final String fullUrl = buildFullUrl(convertPurchaseApiInput, purchase);
        final HttpRequest request = buildHttpRequest(fullUrl);

        try {
            HttpResponse<String> response = sendRequest(request);
            return processResponse(response, purchase);
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON response: {}", e.toString());
            metricsUtils.incrementParsingErrorMetric();
            throw new PurchaseConversionException("Error parsing API response");
        } catch (IOException | InterruptedException e) {
            log.error("Failed to send request with fiscaldata api: {}", e.toString());
            throw new RetryableException("IO/Interrupted Exception during request");
        } catch (Exception e) {
            log.error("Unhandled exception: {}", e.toString());
            metricsUtils.incrementUnmappedExceptionMetric();
            throw new PurchaseConversionException("An unexpected error occurred");
        }
    }

    private Purchase fetchPurchase(ConvertPurchaseApiInput input) {
        return purchaseRepository.findById(input.purchaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found for id: " + input.purchaseId()));
    }

    private String buildFullUrl(ConvertPurchaseApiInput input, Purchase purchase) {
        final var purchaseDate = purchase.getDate();
        final var sixMonthsBefore = purchaseDate.minusMonths(6).toString();
        final var countryCurrency = input.countryCurrency();

        return new ApiUrlBuilder(exchangeApiUrl)
                .addFields(EXCHANGE_RATE, EFFECTIVE_DATE)
                .addFilter(EFFECTIVE_DATE, ParamComparator.GTE, sixMonthsBefore)
                .addFilter(EFFECTIVE_DATE, ParamComparator.LTE, purchaseDate.toString())
                .addFilter(COUNTRY_CURRENCY, ParamComparator.EQ, countryCurrency) //tratar input
                .addSorting(SortOrder.DESC, EFFECTIVE_DATE)
                .build();
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
        metricsUtils.registryExchangeRateRetrievalElapsedTime(stopWatch.getNanoTime());
        return response;
    }

    private ConvertPurchaseApiOutput processResponse(HttpResponse<String> response, Purchase purchase) throws JsonProcessingException {
        if (response.statusCode() == HttpStatus.OK.value()) {
            return parseResponseAndCalculate(response, purchase);
        } else {
            log.warn("Unexpected response status: {}", response.statusCode());
            throw new ResponseStatusException(HttpStatus.valueOf(response.statusCode()), "Unexpected response from API");
        }
    }

    private ConvertPurchaseApiOutput parseResponseAndCalculate(HttpResponse<String> response, Purchase purchase) throws JsonProcessingException {
        ExchangeRateResponse exchangeRateResponse = mapper.readValue(response.body(), ExchangeRateResponse.class);
        log.debug("Fiscal service api call returned with {} exchange rates", exchangeRateResponse.data().size());
        final var mostRecentExchangeRate = extractMostRecentExchangeRate(exchangeRateResponse);

        final var convertedAmount = calculateConvertedAmount(purchase, mostRecentExchangeRate);

        return new ConvertPurchaseApiOutput(
                purchase.getId(),
                purchase.getDescription(),
                purchase.getDate().toString(),
                purchase.getAmount(),
                mostRecentExchangeRate,
                convertedAmount);
    }

    private BigDecimal extractMostRecentExchangeRate(ExchangeRateResponse response) {
        return response.data().stream()
                .findFirst()
                .map(ExchangeRateData::exchangeRate)
                .orElseThrow(() ->
                        new ExchangeRateNotFoundException("Purchase cannot be converted to the target currency")
                );
    }

    private BigDecimal calculateConvertedAmount(Purchase purchase, BigDecimal exchangeRate) {
        return purchase.getAmount().multiply(exchangeRate)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
