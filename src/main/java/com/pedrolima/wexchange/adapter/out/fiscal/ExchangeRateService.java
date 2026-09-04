package com.pedrolima.wexchange.adapter.out.fiscal;

import com.pedrolima.wexchange.adapter.out.persistence.ExchangeRateJpaEntity;
import com.pedrolima.wexchange.adapter.out.persistence.ExchangeRateRepository;
import com.pedrolima.wexchange.application.port.ExchangeRateQuote;
import com.pedrolima.wexchange.application.port.ExchangeRateRefresher;
import com.pedrolima.wexchange.application.port.FiscalDataClient;
import com.pedrolima.wexchange.domain.purchase.Purchase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Refreshes stored exchange rates for a purchase's conversion window.
 *
 * <p>Fetching is delegated entirely to {@link FiscalDataClient}, which owns
 * every retry, circuit-breaker, bulkhead, and timeout decision (issue #3).
 * This class runs asynchronously and does not retry what the client already
 * gave up on - stacking a second retry policy here would just multiply the
 * client's own bounded retries by however many times this method were called.
 */
@Service
@Slf4j
public class ExchangeRateService implements ExchangeRateRefresher {

    private final FiscalDataClient fiscalDataClient;
    private final ExchangeRateRepository exchangeRateRepository;

    public ExchangeRateService(
            final FiscalDataClient fiscalDataClient,
            final ExchangeRateRepository exchangeRateRepository
    ) {
        this.fiscalDataClient = fiscalDataClient;
        this.exchangeRateRepository = exchangeRateRepository;
    }

    @Override
    @Async
    public void refreshFor(final Purchase purchase) {
        final List<ExchangeRateQuote> quotes = fiscalDataClient.fetchExchangeRates(purchase.conversionWindow());
        final List<ExchangeRateJpaEntity> newRates = deduplicateAndFilterNew(quotes);
        if (!newRates.isEmpty()) {
            exchangeRateRepository.saveAll(newRates);
        }
    }

    private List<ExchangeRateJpaEntity> deduplicateAndFilterNew(final List<ExchangeRateQuote> quotes) {
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
                .filter(this::isConversionRateNew)
                .collect(Collectors.toList());
    }

    private boolean isConversionRateNew(final ExchangeRateJpaEntity conversionRate) {
        return exchangeRateRepository.notExistsByCountryCurrencyAndEffectiveDate(
                conversionRate.getCountryCurrency(),
                conversionRate.getEffectiveDate()
        );
    }
}
