package com.pedrolima.wexchange.application;

import com.pedrolima.wexchange.application.port.ExchangeRateLoader;
import com.pedrolima.wexchange.application.port.ExchangeRateStore;
import com.pedrolima.wexchange.application.port.PurchaseStore;
import com.pedrolima.wexchange.domain.error.ExchangeRateNotFoundException;
import com.pedrolima.wexchange.domain.error.MultipleCountryCurrenciesException;
import com.pedrolima.wexchange.domain.error.ResourceNotFoundException;
import com.pedrolima.wexchange.domain.exchange.ExchangeRate;
import com.pedrolima.wexchange.domain.purchase.ConvertedPurchase;
import com.pedrolima.wexchange.domain.purchase.Purchase;

import java.util.List;

/**
 * Finds the rate that applies to a purchase and converts it.
 *
 * <p>Resolution and rate lookup are two separate questions, asked in order:
 * first, how many currencies could the search term mean within the purchase's
 * window; only once that answer is exactly one does a second, exact-match query
 * find its latest eligible rate. Merging the two into a single query is what
 * let two overlapping currencies with different latest dates silently collapse
 * into one match instead of being reported as ambiguous.
 *
 * <p>A cache miss loads through synchronously (issue #4) rather than starting
 * a fire-and-forget refresh and answering {@code 404} before it could ever
 * finish: a valid upstream rate that was one call away is worth waiting a
 * bounded amount of time for, and an upstream failure is a truthful
 * {@link com.pedrolima.wexchange.domain.error.RetryableException}-driven
 * {@code 503}, not a false {@code 404}.
 */
public class ConvertPurchaseService implements ConvertPurchaseUseCase {

    public static final int MIN_COUNTRY_CURRENCY_LENGTH = 3;

    public static final int MAX_COUNTRY_CURRENCY_LENGTH = 100;

    private final PurchaseStore purchases;

    private final ExchangeRateStore rates;

    private final ExchangeRateLoader rateLoader;

    public ConvertPurchaseService(
            final PurchaseStore purchases,
            final ExchangeRateStore rates,
            final ExchangeRateLoader rateLoader
    ) {
        this.purchases = purchases;
        this.rates = rates;
        this.rateLoader = rateLoader;
    }

    @Override
    public ConvertedPurchase execute(final String purchaseId, final String countryCurrency) {
        validate(countryCurrency);

        final var purchase = purchases.findById(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found for id: " + purchaseId));

        final var rate = resolveRate(purchase, countryCurrency);

        return purchase.convertWith(rate);
    }

    private ExchangeRate resolveRate(final Purchase purchase, final String countryCurrency) {
        final var window = purchase.conversionWindow();
        var candidates = rates.resolveCandidates(countryCurrency, window);

        if (candidates.isEmpty()) {
            rateLoader.loadExact(countryCurrency, window);
            candidates = rates.resolveCandidates(countryCurrency, window);
        }

        if (candidates.isEmpty()) {
            throw new ExchangeRateNotFoundException("Exchange rate not found for currency " + countryCurrency);
        }

        if (candidates.size() > 1) {
            throw new MultipleCountryCurrenciesException(describeAmbiguity(countryCurrency, candidates));
        }

        return rates.findLatestExact(candidates.get(0), window)
                .orElseThrow(() -> new ExchangeRateNotFoundException(
                        "Exchange rate not found for currency " + countryCurrency));
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
    private static String describeAmbiguity(final String requested, final List<String> candidates) {
        return candidates.size() + " Country currencies found containing " + requested
                + " it: " + String.join(", ", candidates);
    }
}
