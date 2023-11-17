package com.pedrolima.wexchange.usecases.purchase.convert;

import com.pedrolima.wexchange.api.ApiLink;
import com.pedrolima.wexchange.entities.ExchangeRateJpaEntity;
import com.pedrolima.wexchange.entities.PurchaseJpaEntity;
import com.pedrolima.wexchange.exceptions.ExchangeRateNotFoundException;
import com.pedrolima.wexchange.exceptions.MultipleCountryCurrenciesException;
import com.pedrolima.wexchange.exceptions.ResourceNotFoundException;
import com.pedrolima.wexchange.purchase.models.ConvertPurchaseApiInput;
import com.pedrolima.wexchange.purchase.models.ConvertPurchaseApiOutput;
import com.pedrolima.wexchange.repositories.ExchangeRateRepository;
import com.pedrolima.wexchange.repositories.PurchaseRepository;
import com.pedrolima.wexchange.services.async.ExchangeRateService;
import com.pedrolima.wexchange.utils.ConversionUtils;
import com.pedrolima.wexchange.utils.MetricsHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
 * @see ExchangeRateJpaEntity for details on the currency conversion rates.
 * @see ConvertPurchaseApiOutput for the output format of the currency conversion operation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultConvertPurchaseUseCase extends ConvertPurchaseUseCase {

    public static final int MIN_COUNTRY_CURRENCY_LENGTH = 3;

    public static final int MAX_COUNTRY_CURRENCY_LENGTH = 100;

    private final PurchaseRepository purchaseRepository;

    private final ExchangeRateRepository exchangeRateRepository;

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
                            countryCurrencies.stream().map(ExchangeRateJpaEntity::getCountryCurrency)
                                    .collect(Collectors.joining(", "))
                    ));
        }

        return calculateAndProduceOutput(purchase, countryCurrencies.stream().findFirst().get());
    }

    private List<ExchangeRateJpaEntity> findExchangeRates(
            final ConvertPurchaseApiInput input,
            final PurchaseJpaEntity purchase,
            final Pair<LocalDate, LocalDate> availablePeriod
    ) {
        final var countryCurrencies = exchangeRateRepository.findLatestRatesByCountryCurrencyAndDateRange(
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
        if (isBlank(input.countryCurrency()) || length(input.countryCurrency()) < MIN_COUNTRY_CURRENCY_LENGTH
                || length(input.countryCurrency()) > MAX_COUNTRY_CURRENCY_LENGTH
        ) {
            throw new IllegalArgumentException("Country Currency must have between 3 and 100 characters");
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
            final ExchangeRateJpaEntity conversionRate
    ) {
        final var convertedAmount = ConversionUtils.calculateConvertedAmount(purchase,
                conversionRate.getRateValue());

        return ConvertPurchaseApiOutput.with(
                purchase.getId(),
                purchase.getDescription(),
                purchase.getPurchaseDate().toString(),
                purchase.getAmount(),
                conversionRate.getCountryCurrency(),
                conversionRate.getRateValue(),
                conversionRate.getEffectiveDate().toString(),
                convertedAmount,
                createApiLinks()
        );
    }

    private List<ApiLink> createApiLinks() {
        final var convertParams = Map.of(
                "country_currency", "String: Country-Currency to convert"
        );

        return List.of(
                ApiLink.with("purchase", "/v1/purchases",
                        "POST",
                        Collections.emptyMap()),
                ApiLink.with("country_currencies", "/v1/country_currencies?country_currency=", "GET", convertParams)
        );
    }
}
