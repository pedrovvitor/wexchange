package com.pedrolima.wexchange.domain.exchange;

import com.pedrolima.wexchange.domain.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExchangeRateTest {

    private static final LocalDate EFFECTIVE = LocalDate.of(2024, 1, 31);

    @Test
    @DisplayName("converts an amount at the published rate, rounding once")
    void givenRate_whenConverting_thenAmountIsMultipliedAndRounded() {
        final var rate = new ExchangeRate("Brazil-Real", EFFECTIVE, new BigDecimal("4.925"));

        assertEquals(Money.of("49.25"), rate.convert(Money.of("10.00")));
    }

    @Test
    @DisplayName("keeps the rate's own precision rather than pre-rounding it")
    void givenHighPrecisionRate_whenConverting_thenPrecisionIsNotLostFirst() {
        final var rate = new ExchangeRate("Brazil-Real", EFFECTIVE, new BigDecimal("0.005"));

        // Pre-rounding the rate to two places would give 0.01 x 100.00 = 1.00.
        assertEquals(Money.of("0.50"), rate.convert(Money.of("100.00")));
    }

    @Test
    @DisplayName("null components are rejected rather than carried")
    void givenNullComponent_whenConstructing_thenItIsRejected() {
        assertThrows(NullPointerException.class, () -> new ExchangeRate(null, EFFECTIVE, BigDecimal.ONE));
        assertThrows(NullPointerException.class, () -> new ExchangeRate("Brazil-Real", null, BigDecimal.ONE));
        assertThrows(NullPointerException.class, () -> new ExchangeRate("Brazil-Real", EFFECTIVE, null));
        assertThrows(NullPointerException.class,
                () -> new ExchangeRate("Brazil-Real", EFFECTIVE, BigDecimal.ONE).convert(null));
    }
}
