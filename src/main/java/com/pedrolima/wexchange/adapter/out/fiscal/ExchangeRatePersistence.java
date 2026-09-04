package com.pedrolima.wexchange.adapter.out.fiscal;

import com.pedrolima.wexchange.adapter.out.persistence.ExchangeRateJpaEntity;
import com.pedrolima.wexchange.adapter.out.persistence.ExchangeRateRepository;
import com.pedrolima.wexchange.application.port.ExchangeRateQuote;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns freshly fetched quotes into the rows worth persisting: at most one row
 * per country-currency in the batch (the first kept, later duplicates
 * dropped), and only rows the repository does not already have for that exact
 * currency and date. Shared by both the broad, asynchronous warm-up
 * ({@link ExchangeRateService}) and the bounded, synchronous per-currency
 * load-through (issue #4's {@link SynchronousExchangeRateLoader}), which would
 * otherwise duplicate this exact logic.
 */
final class ExchangeRatePersistence {

    private ExchangeRatePersistence() {
    }

    static List<ExchangeRateJpaEntity> deduplicateAndFilterNew(
            final List<ExchangeRateQuote> quotes,
            final ExchangeRateRepository repository
    ) {
        return quotes.stream()
                .map(quote -> ExchangeRateJpaEntity.newConversionRate(
                        quote.countryCurrency(), quote.effectiveDate(), quote.exchangeRate()))
                .collect(Collectors.toMap(
                        ExchangeRateJpaEntity::getCountryCurrency,
                        Function.identity(),
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .filter(conversionRate -> repository.notExistsByCountryCurrencyAndEffectiveDate(
                        conversionRate.getCountryCurrency(), conversionRate.getEffectiveDate()))
                .collect(Collectors.toList());
    }

    static void persistNew(final List<ExchangeRateQuote> quotes, final ExchangeRateRepository repository) {
        final List<ExchangeRateJpaEntity> newRates = deduplicateAndFilterNew(quotes, repository);
        if (!newRates.isEmpty()) {
            repository.saveAll(newRates);
        }
    }
}
