package com.pedrolima.wexchange.usecases.purchase.convert;

import com.pedrolima.wexchange.api.purchase.ConvertPurchaseApiInput;
import com.pedrolima.wexchange.api.purchase.ConvertPurchaseApiOutput;
import com.pedrolima.wexchange.entities.ConversionRateJpaEntity;
import com.pedrolima.wexchange.entities.PurchaseJpaEntity;
import com.pedrolima.wexchange.exceptions.ExchangeRateNotFoundException;
import com.pedrolima.wexchange.exceptions.MultipleCountryCurrenciesException;
import com.pedrolima.wexchange.exceptions.ResourceNotFoundException;
import com.pedrolima.wexchange.repositories.ConversionRateRepository;
import com.pedrolima.wexchange.repositories.PurchaseRepository;
import com.pedrolima.wexchange.services.async.ExchangeRateService;
import com.pedrolima.wexchange.utils.ConversionUtils;
import com.pedrolima.wexchange.utils.MetricsHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static com.pedrolima.wexchange.utils.ConversionUtils.calculateConversionAvailablePeriod;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.length;

/**
 * Service class responsible for converting a given purchase into a requested country currency.
 * This class extends {@link ConvertPurchaseUseCase} and provides detailed logic for currency conversion of purchases.
 * <p>
 * The service performs the following operations:
 * - Validates the input for country currency format.
 * - Fetches the corresponding purchase entity from the database.
 * - Retrieves the latest conversion rate for the specified country currency within a calculated available period.
 * - Handles cases where no exchange rate is found or multiple rates are found for the given criteria.
 * - Calculates the converted amount using the obtained exchange rate.
 * - Produces and returns the output with details of the original purchase and its converted values.
 * <p>
 * Exception Handling:
 * - Throws {@link IllegalArgumentException} if the country currency format is invalid.
 * - Throws {@link ExchangeRateNotFoundException} if no exchange rate is found for the specified currency and period.
 * - Throws {@link MultipleCountryCurrenciesException} if multiple rates are found for the given currency.
 * - Throws {@link ResourceNotFoundException} if the purchase with the given ID is not found.
 *
 * @see PurchaseJpaEntity for details on the purchase entity.
 * @see ConversionRateJpaEntity for details on the currency conversion rates.
 * @see ConvertPurchaseApiOutput for the output format of the currency conversion operation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultConvertPurchaseUseCase extends ConvertPurchaseUseCase {

    private final PurchaseRepository purchaseRepository;

    private final ConversionRateRepository conversionRateRepository;

    private final ExchangeRateService exchangeRateService;

    private final MetricsHelper metricsHelper;

    @Override
    public ConvertPurchaseApiOutput execute(final ConvertPurchaseApiInput input) {
        validateInput(input);

        final var purchase = fetchPurchase(input.purchaseId());
        final var availablePeriod = calculateConversionAvailablePeriod(purchase);
        final var countryCurrencies = findExchangeRates(input, purchase, availablePeriod);

        if (countryCurrencies.size() > 1) {
            throw new MultipleCountryCurrenciesException("%d Country currencies found containing %s it: %s"
                    .formatted(
                            countryCurrencies.size(),
                            input.countryCurrency(),
                            countryCurrencies.stream().map(ConversionRateJpaEntity::getCountryCurrency)
                                    .collect(Collectors.joining("\\n, "))
                    ));
        }

        return calculateAndProduceOutput(purchase, countryCurrencies.stream().findFirst().get());
    }

    private List<ConversionRateJpaEntity> findExchangeRates(
            final ConvertPurchaseApiInput input,
            final PurchaseJpaEntity purchase,
            final Pair<LocalDate, LocalDate> availablePeriod
    ) {
        final var countryCurrencies = conversionRateRepository.findLatestRatesByCountryCurrencyAndDateRange(
                input.countryCurrency(),
                availablePeriod.getLeft(),
                availablePeriod.getRight()
        );

        if (countryCurrencies.isEmpty()) {
            log.debug("Exchange rate not found for currency {} during {}", input.countryCurrency(), availablePeriod);
            exchangeRateService.updateExchangeRates(purchase);
            throw new ExchangeRateNotFoundException("Exchange rate not found for currency " + input.countryCurrency());
        }

        return countryCurrencies;
    }

    private static void validateInput(final ConvertPurchaseApiInput input) {
        if (isBlank(input.countryCurrency()) || length(input.countryCurrency()) < 3) {
            throw new IllegalArgumentException("Input %s should be in '{country_name}-{currency_name}' format"
                    .formatted(input.countryCurrency()));
        }
    }

    private PurchaseJpaEntity fetchPurchase(final String purchaseId) {
        return purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> {
                    log.error("Purchase not found for id: {}", purchaseId);
                    return new ResourceNotFoundException("Purchase not found for id: " + purchaseId);
                });
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
