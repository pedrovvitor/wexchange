package com.pedrolima.wexchange.utils;

import com.pedrolima.wexchange.entities.PurchaseJpaEntity;
import org.apache.commons.lang3.tuple.Pair;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public final class ConversionUtils {

    private ConversionUtils() {
    }

    public static Pair<LocalDate, LocalDate> calculateConversionAvailablePeriod(final PurchaseJpaEntity purchase) {
        final var sixMonthsBefore = purchase.getDate().minusMonths(6);
        return Pair.of(sixMonthsBefore, purchase.getDate());
    }

    public static BigDecimal calculateConvertedAmount(final PurchaseJpaEntity purchase, final BigDecimal exchangeRate) {
        return purchase.getAmount().multiply(exchangeRate)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
