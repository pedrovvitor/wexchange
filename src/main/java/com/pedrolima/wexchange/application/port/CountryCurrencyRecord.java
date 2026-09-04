package com.pedrolima.wexchange.application.port;

import java.util.Objects;

/**
 * One validated country-currency pairing as returned by {@link FiscalDataClient}.
 */
public record CountryCurrencyRecord(String countryCurrency, String country, String currency) {

    public CountryCurrencyRecord {
        Objects.requireNonNull(countryCurrency, "countryCurrency must not be null");
        Objects.requireNonNull(country, "country must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (countryCurrency.isBlank() || country.isBlank() || currency.isBlank()) {
            throw new IllegalArgumentException("countryCurrency, country, and currency must not be blank");
        }
    }
}
