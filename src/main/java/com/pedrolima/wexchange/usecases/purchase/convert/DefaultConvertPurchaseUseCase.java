package com.pedrolima.wexchange.usecases.purchase.convert;

import com.pedrolima.wexchange.api.purchase.ConvertPurchaseApiInput;
import com.pedrolima.wexchange.api.purchase.ConvertPurchaseApiOutput;
import com.pedrolima.wexchange.entities.ConversionRateJpaEntity;
import com.pedrolima.wexchange.entities.PurchaseJpaEntity;
import com.pedrolima.wexchange.exceptions.ExchangeRateNotFoundException;
import com.pedrolima.wexchange.exceptions.MultipleCountryCurrenciesException;
import com.pedrolima.wexchange.exceptions.ResourceNotFoundException;
import com.pedrolima.wexchange.exceptions.RetryableException;
import com.pedrolima.wexchange.repositories.ConversionRateRepository;
import com.pedrolima.wexchange.repositories.PurchaseRepository;
import com.pedrolima.wexchange.utils.ConversionUtils;
import com.pedrolima.wexchange.utils.MetricsHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

import static com.pedrolima.wexchange.utils.ConversionUtils.calculateConversionAvailablePeriod;

@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultConvertPurchaseUseCase extends ConvertPurchaseUseCase {

    private final PurchaseRepository purchaseRepository;

    private final ConversionRateRepository conversionRateRepository;

    private final MetricsHelper metricsHelper;

    @Override
    @Retryable(retryFor = {RetryableException.class}, backoff = @Backoff(delay = 1000))
    public ConvertPurchaseApiOutput execute(final ConvertPurchaseApiInput input) {
        if (StringUtils.isBlank(input.countryCurrency())) {
            throw new IllegalArgumentException("Input %s should be in '{country_name}-{currency_name}' format"
                    .formatted(input.countryCurrency()));
        }

        final var purchase = fetchPurchase(input);
        final var availablePeriod = calculateConversionAvailablePeriod(purchase);
        final var countryCurrencies = conversionRateRepository.findLatestRatesByCountryCurrencyAndDateRange(
                input.countryCurrency(),
                availablePeriod.getLeft(),
                availablePeriod.getRight()
        );

        if (countryCurrencies.isEmpty()) {
            throw new ExchangeRateNotFoundException(
                    "Exchange rate not found for currency {%s} on period %s-%s"
                            .formatted(
                                    input.countryCurrency(),
                                    availablePeriod.getLeft(),
                                    availablePeriod.getRight()
                            )
            );
        }
        if (countryCurrencies.size() > 1) {
            throw new MultipleCountryCurrenciesException("%d Country currencies found with name %s: %s".formatted(
                    countryCurrencies.size(),
                    input.countryCurrency(),
                    countryCurrencies.stream().map(ConversionRateJpaEntity::getCountryCurrency)
                            .collect(Collectors.joining("\n, "))
            ));
        }

        return calculateAndProduceOutput(purchase, countryCurrencies.stream().findFirst().get());
    }

    private PurchaseJpaEntity fetchPurchase(ConvertPurchaseApiInput input) {
        return purchaseRepository.findById(input.purchaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found for id: " + input.purchaseId()));
    }

    private ConvertPurchaseApiOutput calculateAndProduceOutput(
            final PurchaseJpaEntity purchase,
            final ConversionRateJpaEntity conversionRate
    ) {
        final var convertedAmount = ConversionUtils.calculateConvertedAmount(purchase,
                conversionRate.getExchangeRate());

        return ConvertPurchaseApiOutput.with(
                purchase.getId(),
                purchase.getDescription(),
                purchase.getDate().toString(),
                purchase.getAmount(),
                conversionRate.getCountryCurrency(),
                conversionRate.getExchangeRate(),
                conversionRate.getEffectiveDate().toString(),
                convertedAmount
        );
    }
}
