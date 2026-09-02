package com.pedrolima.wexchange.domain.money;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rounding is one of the two financial invariants of the product, so this class
 * is a mutation-testing target: every assertion exists to kill a specific class
 * of mutant, and {@code ./gradlew mutationTest} fails if any survives.
 *
 * <p>Values are exact {@link BigDecimal} literals. A test written with
 * {@code double} could pass against an incorrect implementation.
 */
class MoneyTest {

    @Test
    @DisplayName("normalises to two decimal places on construction")
    void givenUnscaledAmount_whenConstructing_thenScaleIsTwo() {
        assertEquals(new BigDecimal("10.00"), Money.of("10").amount());
        assertEquals(2, Money.of("10").amount().scale());
        assertEquals(new BigDecimal("6.00"), new Money(new BigDecimal("6")).amount());
    }

    @ParameterizedTest(name = "{0} x {1} = {2}")
    @DisplayName("rounds half to even, so ties do not drift in one party's favour")
    @CsvSource({
            // Tie rounding down to the even neighbour.
            "1.00, 0.125, 0.12",
            // Tie rounding up to the even neighbour.
            "1.00, 0.135, 0.14",
            // Above the tie always rounds up.
            "1.00, 0.1251, 0.13",
            // Below the tie always rounds down.
            "1.00, 0.1249, 0.12",
    })
    void givenTieValues_whenMultiplying_thenHalfEvenRoundingIsApplied(
            final String amount, final String factor, final String expected) {
        assertEquals(Money.of(expected), Money.of(amount).multipliedBy(new BigDecimal(factor)));
    }

    @Test
    @DisplayName("keeps the factor's precision and rounds only the product")
    void givenPreciseFactor_whenMultiplying_thenProductIsRoundedOnce() {
        assertEquals(Money.of("49.25"), Money.of("10.00").multipliedBy(new BigDecimal("4.925")));
    }

    @Test
    @DisplayName("a zero factor yields zero, not the original amount")
    void givenZeroFactor_whenMultiplying_thenResultIsZero() {
        assertEquals(Money.of("0.00"), Money.of("99.99").multipliedBy(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("a factor of one returns the original amount, not a constant")
    void givenUnitFactor_whenMultiplying_thenAmountIsUnchanged() {
        assertEquals(Money.of("123.45"), Money.of("123.45").multipliedBy(BigDecimal.ONE));
    }

    @Test
    @DisplayName("large amounts keep full precision instead of overflowing")
    void givenLargeAmount_whenMultiplying_thenPrecisionIsPreserved() {
        assertEquals(Money.of("123456779987.65"),
                Money.of("99999999.99").multipliedBy(new BigDecimal("1234.5678")));
    }

    @Test
    @DisplayName("reports whether the amount is below zero")
    void givenAmounts_whenCheckingSign_thenOnlyBelowZeroIsNegative() {
        assertTrue(Money.of("-0.01").isNegative());
        assertFalse(Money.of("0.00").isNegative());
        assertFalse(Money.of("0.01").isNegative());
    }

    @Test
    @DisplayName("equality follows the normalised amount")
    void givenEquivalentAmounts_whenComparing_thenTheyAreEqual() {
        assertEquals(Money.of("10.0"), Money.of("10.00"));
        assertEquals(Money.of("10.0").hashCode(), Money.of("10.00").hashCode());
    }

    @Test
    @DisplayName("prints without exponent notation")
    void givenAmount_whenPrinting_thenPlainStringIsUsed() {
        assertEquals("0.00", Money.of("1E-9").toString());
        assertEquals("123.45", Money.of("123.45").toString());
    }

    @Test
    @DisplayName("a null amount or factor is rejected rather than carried")
    void givenNull_whenUsed_thenItIsRejected() {
        assertThrows(NullPointerException.class, () -> new Money(null));
        assertThrows(NullPointerException.class, () -> Money.of("1.00").multipliedBy(null));
    }
}
