package com.pedrolima.wexchange.application.port;

import com.pedrolima.wexchange.domain.exchange.ConversionWindow;
import com.pedrolima.wexchange.domain.exchange.ExchangeRate;

import java.util.List;
import java.util.Optional;

/** Storage for exchange rates already retrieved from the provider. */
public interface ExchangeRateStore {

    /**
     * Every distinct country-currency, within the window, whose descriptor
     * contains the given term. More than one result means the term was
     * ambiguous, which is a decision for the caller rather than for storage.
     */
    List<String> resolveCandidates(String countryCurrency, ConversionWindow window);

    /** The latest eligible rate for one exact, already-resolved country-currency. */
    Optional<ExchangeRate> findLatestExact(String countryCurrency, ConversionWindow window);
}
