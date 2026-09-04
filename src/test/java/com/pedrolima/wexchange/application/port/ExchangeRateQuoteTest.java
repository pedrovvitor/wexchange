package com.pedrolima.wexchange.application.port;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExchangeRateQuoteTest {

    private static final LocalDate A_DATE = LocalDate.of(2024, 1, 1);
    private static final BigDecimal A_RATE = new BigDecimal("5.22");

    @Test
    void givenValidFields_whenConstructed_thenTheyAreKept() {
        final var quote = new ExchangeRateQuote("Brazil-Real", A_DATE, A_RATE);

        assertEquals("Brazil-Real", quote.countryCurrency());
        assertEquals(A_DATE, quote.effectiveDate());
        assertEquals(A_RATE, quote.exchangeRate());
    }

    @Test
    void givenANullCountryCurrency_whenConstructed_thenItIsRejected() {
        assertThrows(NullPointerException.class, () -> new ExchangeRateQuote(null, A_DATE, A_RATE));
    }

    @Test
    void givenABlankCountryCurrency_whenConstructed_thenItIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ExchangeRateQuote("  ", A_DATE, A_RATE));
    }

    @Test
    void givenANullEffectiveDate_whenConstructed_thenItIsRejected() {
        assertThrows(NullPointerException.class, () -> new ExchangeRateQuote("Brazil-Real", null, A_RATE));
    }

    @Test
    void givenANullExchangeRate_whenConstructed_thenItIsRejected() {
        assertThrows(NullPointerException.class, () -> new ExchangeRateQuote("Brazil-Real", A_DATE, null));
    }

    @Test
    void givenAZeroExchangeRate_whenConstructed_thenItIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ExchangeRateQuote("Brazil-Real", A_DATE, BigDecimal.ZERO));
    }

    @Test
    void givenANegativeExchangeRate_whenConstructed_thenItIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExchangeRateQuote("Brazil-Real", A_DATE, new BigDecimal("-1")));
    }
}
