package com.pedrolima.wexchange.domain.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A monetary amount, always carried at two decimal places.
 *
 * <p>Normalising in the constructor rather than at each point of use means a
 * wrongly scaled amount cannot exist as a value. Rounding is
 * {@link RoundingMode#HALF_EVEN} so ties do not drift consistently in one
 * party's favour across many conversions.
 *
 * <p>Because every instance is already at scale 2, the record's generated
 * equality is correct: the {@code 10.00} versus {@code 10.0} trap that makes
 * {@link BigDecimal#equals} surprising cannot arise here.
 */
public record Money(BigDecimal amount) {

    public static final int SCALE = 2;

    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        amount = amount.setScale(SCALE, ROUNDING);
    }

    public static Money of(final String amount) {
        return new Money(new BigDecimal(amount));
    }

    /**
     * Multiplies by an unrounded factor and rounds once, at the end.
     *
     * <p>The factor keeps its own precision deliberately: an exchange rate is
     * quoted to more decimal places than money is held in, and rounding the rate
     * before multiplying would discard value that belongs in the result.
     */
    public Money multipliedBy(final BigDecimal factor) {
        Objects.requireNonNull(factor, "factor must not be null");
        return new Money(amount.multiply(factor));
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
