package com.pedrolima.wexchange.application.port;

import com.pedrolima.wexchange.domain.exchange.ConversionWindow;
import com.pedrolima.wexchange.domain.exchange.ExchangeRate;

import java.util.List;

/** Storage for exchange rates already retrieved from the provider. */
public interface ExchangeRateStore {

    /**
     * The most recent rate within the window for each country-currency whose
     * descriptor contains the given term. More than one result means the term
     * was ambiguous, which is a decision for the caller rather than for storage.
     */
    List<ExchangeRate> findLatestWithin(String countryCurrency, ConversionWindow window);
}
