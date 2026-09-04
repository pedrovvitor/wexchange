package com.pedrolima.wexchange.adapter.out.fiscal;

import com.pedrolima.wexchange.adapter.out.persistence.ExchangeRateRepository;
import com.pedrolima.wexchange.application.port.ExchangeRateRefresher;
import com.pedrolima.wexchange.application.port.FiscalDataClient;
import com.pedrolima.wexchange.domain.purchase.Purchase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
        final var quotes = fiscalDataClient.fetchExchangeRates(purchase.conversionWindow());
        ExchangeRatePersistence.persistNew(quotes, exchangeRateRepository);
    }
}
