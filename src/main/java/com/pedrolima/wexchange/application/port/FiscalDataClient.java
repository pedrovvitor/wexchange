package com.pedrolima.wexchange.application.port;

import com.pedrolima.wexchange.domain.exchange.ConversionWindow;

import java.util.List;

/**
 * One resilient gateway to the upstream fiscal data provider.
 *
 * <p>Bounded latency, retry, circuit-breaker, bulkhead, and schema-validation
 * behaviour all live behind this port, in the single adapter that implements
 * it. Callers never retry what this port already returned or threw: a
 * {@link com.pedrolima.wexchange.domain.error.RetryableException} from either
 * method means every retry this port is willing to make has already run out.
 */
public interface FiscalDataClient {

    /** Exchange rates whose effective date falls within the given window. */
    List<ExchangeRateQuote> fetchExchangeRates(ConversionWindow window);

    /**
     * Exchange rates for exactly one country-currency within the given
     * window (issue #4) - bounded, so a conversion request's synchronous
     * cache-miss load-through never pulls every currency the provider
     * publishes just to answer for the one the caller asked about.
     */
    List<ExchangeRateQuote> fetchExchangeRates(String exactCountryCurrency, ConversionWindow window);

    /** Every country-currency the provider currently publishes. */
    List<CountryCurrencyRecord> fetchCountryCurrencies();
}
