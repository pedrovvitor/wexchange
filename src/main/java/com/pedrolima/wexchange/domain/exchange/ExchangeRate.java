package com.pedrolima.wexchange.domain.exchange;

import com.pedrolima.wexchange.domain.money.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A published rate for one country-currency on one date.
 *
 * <p>The rate keeps the precision it was published with. Rounding happens once,
 * inside {@link Money}, when the converted amount is produced.
 */
public record ExchangeRate(String countryCurrency, LocalDate effectiveDate, BigDecimal rateValue) {

    public ExchangeRate {
        Objects.requireNonNull(countryCurrency, "countryCurrency must not be null");
        Objects.requireNonNull(effectiveDate, "effectiveDate must not be null");
        Objects.requireNonNull(rateValue, "rateValue must not be null");
    }

    public Money convert(final Money amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        return amount.multipliedBy(rateValue);
    }
}
