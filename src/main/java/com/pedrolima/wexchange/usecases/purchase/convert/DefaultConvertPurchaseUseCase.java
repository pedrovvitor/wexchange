package com.pedrolima.wexchange.usecases.purchase.convert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedrolima.wexchange.api.purchase.ConvertPurchaseApiInput;
import com.pedrolima.wexchange.api.purchase.ConvertPurchaseApiOutput;
import com.pedrolima.wexchange.bean.exchange.ExchangeRateData;
import com.pedrolima.wexchange.bean.exchange.ExchangeRateResponse;
import com.pedrolima.wexchange.exception.ExchangeRateNotFoundException;
import com.pedrolima.wexchange.exception.PurchaseConversionException;
import com.pedrolima.wexchange.exception.ResourceNotFoundException;
import com.pedrolima.wexchange.repositories.PurchaseRepository;
import com.pedrolima.wexchange.util.MetricsUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
@Slf4j
public class DefaultConvertPurchaseUseCase extends ConvertPurchaseUseCase {

    @Value("${fiscal.service.api.endpoint}")
    private String exchangeUrlApi;

    public DefaultConvertPurchaseUseCase(final PurchaseRepository purchaseRepository, final ObjectMapper mapper, final MetricsUtils metricsUtils) {
        this.purchaseRepository = purchaseRepository;
        this.mapper = mapper;
        this.metricsUtils = metricsUtils;
    }
    private final PurchaseRepository purchaseRepository;

    private final ObjectMapper mapper;

    private final MetricsUtils metricsUtils;
    @Override
    public ConvertPurchaseApiOutput execute(final ConvertPurchaseApiInput convertPurchaseApiInput) {
        final var purchase = purchaseRepository.findById(convertPurchaseApiInput.purchaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found for id: " + convertPurchaseApiInput.purchaseId()));

        final var purchaseDate = purchase.getDate();
        final var sixMonthsBefore = purchaseDate.minusMonths(6);

        String filterParams = String.format("filter=record_date:lte:%s,record_date:gte:%s", purchaseDate, sixMonthsBefore);
        String fullUrl = exchangeUrlApi + "?fields=exchange_rate&" + filterParams;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .GET()
                .build();

        final ExchangeRateResponse exchangeRateResponse;
        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            stopWatch.stop();
            metricsUtils.registryExchangeRateRetrievalElapsedTime(stopWatch.getNanoTime());

            exchangeRateResponse = mapper.readValue(response.body(), ExchangeRateResponse.class);
        } catch (JsonProcessingException e) {
            metricsUtils.incrementParsingErrorMetric();
            throw new PurchaseConversionException();
        } catch (IOException | InterruptedException e) {
            metricsUtils.incrementRequestErrorMetric();
            throw new PurchaseConversionException();
        }

        log.debug("Fiscal service api call returned with {} exchange rates", exchangeRateResponse.data().size());
        final var mostRecentExchangeRate = exchangeRateResponse
                .data()
                .stream()
                .findFirst()
                .map(ExchangeRateData::exchangeRate)
                .orElseThrow(
                        () -> new ExchangeRateNotFoundException());

        BigDecimal convertedAmount = purchase.getAmount().multiply(mostRecentExchangeRate)
                .setScale(2, RoundingMode.HALF_UP);

        return new ConvertPurchaseApiOutput(
                purchase.getId(),
                purchase.getDescription(),
                purchase.getDate(),
                purchase.getAmount(),
                mostRecentExchangeRate,
                convertedAmount);
    }
}
