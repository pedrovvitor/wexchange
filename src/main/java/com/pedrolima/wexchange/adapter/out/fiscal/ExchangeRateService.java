package com.pedrolima.wexchange.adapter.out.fiscal;

import com.pedrolima.wexchange.adapter.out.persistence.ExchangeRateUpsertRepository;
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
 * Persisting is a single bulk upsert (issue #6): a concurrent refresh for the
 * exact same window is safe by construction, not by a check this class makes.
 */
@Service
@Slf4j
public class ExchangeRateService implements ExchangeRateRefresher {

    private final FiscalDataClient fiscalDataClient;
    private final ExchangeRateUpsertRepository exchangeRateUpsertRepository;

    public ExchangeRateService(
            final FiscalDataClient fiscalDataClient,
            final ExchangeRateUpsertRepository exchangeRateUpsertRepository
    ) {
        this.fiscalDataClient = fiscalDataClient;
        this.exchangeRateUpsertRepository = exchangeRateUpsertRepository;
    }

    @Override
    @Async
    public void refreshFor(final Purchase purchase) {
        final var quotes = fiscalDataClient.fetchExchangeRates(purchase.conversionWindow());
        exchangeRateUpsertRepository.upsertAll(quotes);
    }
}
