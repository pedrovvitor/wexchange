package com.pedrolima.wexchange.domain.exchange;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The dates whose exchange rates may be applied to a purchase: the six months
 * preceding the purchase date, both ends inclusive.
 *
 * <p>When the earlier month is shorter than the purchase month the start clamps
 * to that month's last valid day. That is {@link LocalDate#minusMonths}
 * behaviour, and it is asserted rather than assumed.
 */
public record ConversionWindow(LocalDate start, LocalDate end) {

    public static final int MONTHS = 6;

    public static ConversionWindow endingOn(final LocalDate purchaseDate) {
        Objects.requireNonNull(purchaseDate, "purchaseDate must not be null");
        return new ConversionWindow(purchaseDate.minusMonths(MONTHS), purchaseDate);
    }
}
