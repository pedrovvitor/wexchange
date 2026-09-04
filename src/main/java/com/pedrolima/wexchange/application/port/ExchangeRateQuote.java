package com.pedrolima.wexchange.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One validated exchange rate as returned by {@link FiscalDataClient}: already
 * checked for required fields and a positive rate, so nothing downstream of
 * this port needs to re-validate the provider's shape.
 */
public record ExchangeRateQuote(String countryCurrency, LocalDate effectiveDate, BigDecimal exchangeRate) {

    public ExchangeRateQuote {
        Objects.requireNonNull(countryCurrency, "countryCurrency must not be null");
        Objects.requireNonNull(effectiveDate, "effectiveDate must not be null");
        Objects.requireNonNull(exchangeRate, "exchangeRate must not be null");
        if (countryCurrency.isBlank()) {
            throw new IllegalArgumentException("countryCurrency must not be blank");
        }
        if (exchangeRate.signum() <= 0) {
            throw new IllegalArgumentException("exchangeRate must be positive: " + exchangeRate);
        }
    }
}
