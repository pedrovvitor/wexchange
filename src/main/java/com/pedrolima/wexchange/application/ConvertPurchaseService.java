package com.pedrolima.wexchange.application;

import com.pedrolima.wexchange.application.port.ExchangeRateRefresher;
import com.pedrolima.wexchange.application.port.ExchangeRateStore;
import com.pedrolima.wexchange.application.port.PurchaseStore;
import com.pedrolima.wexchange.domain.error.ExchangeRateNotFoundException;
import com.pedrolima.wexchange.domain.error.MultipleCountryCurrenciesException;
import com.pedrolima.wexchange.domain.error.ResourceNotFoundException;
import com.pedrolima.wexchange.domain.exchange.ExchangeRate;
import com.pedrolima.wexchange.domain.purchase.ConvertedPurchase;
import com.pedrolima.wexchange.domain.purchase.Purchase;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Finds the rate that applies to a purchase and converts it.
 *
 * <p>A search term matching more than one country-currency is rejected rather
 * than resolved arbitrarily: picking one silently would return a plausible
 * figure computed against the wrong currency.
 */
public class ConvertPurchaseService implements ConvertPurchaseUseCase {

    public static final int MIN_COUNTRY_CURRENCY_LENGTH = 3;

    public static final int MAX_COUNTRY_CURRENCY_LENGTH = 100;

    private final PurchaseStore purchases;

    private final ExchangeRateStore rates;

    private final ExchangeRateRefresher rateRefresher;

    public ConvertPurchaseService(
            final PurchaseStore purchases,
            final ExchangeRateStore rates,
            final ExchangeRateRefresher rateRefresher
    ) {
        this.purchases = purchases;
        this.rates = rates;
        this.rateRefresher = rateRefresher;
    }

    @Override
    public ConvertedPurchase execute(final String purchaseId, final String countryCurrency) {
        validate(countryCurrency);

        final var purchase = purchases.findById(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found for id: " + purchaseId));

        final var matches = findRates(purchase, countryCurrency);

        if (matches.size() > 1) {
            throw new MultipleCountryCurrenciesException(describeAmbiguity(countryCurrency, matches));
        }

        return purchase.convertWith(matches.get(0));
    }

    private List<ExchangeRate> findRates(final Purchase purchase, final String countryCurrency) {
        final var matches = rates.findLatestWithin(countryCurrency, purchase.conversionWindow());

        if (matches.isEmpty()) {
            rateRefresher.refreshFor(purchase);
            throw new ExchangeRateNotFoundException("Exchange rate not found for currency " + countryCurrency);
        }

        return matches;
    }

    private static void validate(final String countryCurrency) {
        if (countryCurrency == null
                || countryCurrency.isBlank()
                || countryCurrency.length() < MIN_COUNTRY_CURRENCY_LENGTH
                || countryCurrency.length() > MAX_COUNTRY_CURRENCY_LENGTH) {
            throw new IllegalArgumentException("Country Currency must have between 3 and 100 characters");
        }
    }

    /**
     * Written as concatenation rather than {@code String.formatted}: a varargs
     * call carries an array-length constant that mutation testing can widen
     * without changing the message, and this class must leave no surviving
     * mutant. See docs/engineering/test-taxonomy.md.
     */
    private static String describeAmbiguity(final String requested, final List<ExchangeRate> matches) {
        final var matched = matches.stream()
                .map(ExchangeRate::countryCurrency)
                .collect(Collectors.joining(", "));

        return matches.size() + " Country currencies found containing " + requested + " it: " + matched;
    }
}
