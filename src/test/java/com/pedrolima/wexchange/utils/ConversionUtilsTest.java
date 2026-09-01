package com.pedrolima.wexchange.utils;

import com.pedrolima.wexchange.entities.PurchaseJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The rounding and conversion-window rules are the two financial invariants of
 * the product, so this class is a mutation-testing target: every assertion here
 * exists to kill a specific class of mutant, and
 * {@code ./gradlew mutationTest} fails if any survives.
 *
 * <p>Values are chosen as exact {@link BigDecimal} literals. A test that used
 * {@code double} could pass against an incorrect implementation.
 */
class ConversionUtilsTest {

    @Nested
    @DisplayName("converted amount")
    class ConvertedAmount {

        @Test
        @DisplayName("multiplies the purchase amount by the rate and keeps two decimal places")
        void givenAmountAndRate_whenConverting_thenProductIsScaledToTwoDecimals() {
            final var purchase = purchaseOf("10.00");

            final var converted = ConversionUtils.calculateConvertedAmount(purchase, new BigDecimal("4.925"));

            assertEquals(new BigDecimal("49.25"), converted);
            assertEquals(2, converted.scale());
        }

        @ParameterizedTest(name = "{0} x {1} = {2}")
        @DisplayName("rounds half to even, so ties do not drift in the merchant's favour")
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
        void givenTieValues_whenConverting_thenHalfEvenRoundingIsApplied(
                final String amount,
                final String rate,
                final String expected
        ) {
            final var converted = ConversionUtils.calculateConvertedAmount(purchaseOf(amount), new BigDecimal(rate));

            assertEquals(new BigDecimal(expected), converted);
        }

        @Test
        @DisplayName("keeps two decimal places even when the product needs padding")
        void givenExactProduct_whenConverting_thenScaleIsPadded() {
            final var converted = ConversionUtils.calculateConvertedAmount(purchaseOf("2.00"), new BigDecimal("3"));

            assertEquals(new BigDecimal("6.00"), converted);
        }

        @Test
        @DisplayName("a zero rate converts to zero rather than to the original amount")
        void givenZeroRate_whenConverting_thenResultIsZero() {
            final var converted = ConversionUtils.calculateConvertedAmount(purchaseOf("99.99"), BigDecimal.ZERO);

            assertEquals(new BigDecimal("0.00"), converted);
        }

        @Test
        @DisplayName("a rate of one returns the original amount, not a rescaled constant")
        void givenUnitRate_whenConverting_thenAmountIsUnchanged() {
            final var converted = ConversionUtils.calculateConvertedAmount(purchaseOf("123.45"), BigDecimal.ONE);

            assertEquals(new BigDecimal("123.45"), converted);
        }

        @Test
        @DisplayName("large amounts keep full precision instead of overflowing")
        void givenLargeAmount_whenConverting_thenPrecisionIsPreserved() {
            final var converted =
                    ConversionUtils.calculateConvertedAmount(purchaseOf("99999999.99"), new BigDecimal("1234.5678"));

            assertEquals(new BigDecimal("123456779987.65"), converted);
        }
    }

    @Nested
    @DisplayName("conversion window")
    class ConversionWindow {

        @Test
        @DisplayName("spans exactly the six months preceding the purchase date, inclusive of it")
        void givenPurchaseDate_whenCalculatingWindow_thenItStartsSixMonthsEarlier() {
            final var purchaseDate = LocalDate.of(2024, 7, 15);

            final var window = ConversionUtils.calculateConversionAvailablePeriod(purchaseOn(purchaseDate));

            assertEquals(LocalDate.of(2024, 1, 15), window.getLeft());
            assertEquals(purchaseDate, window.getRight());
        }

        @Test
        @DisplayName("the window ends on the purchase date, never on today")
        void givenPastPurchase_whenCalculatingWindow_thenUpperBoundIsThePurchaseDate() {
            final var purchaseDate = LocalDate.of(2020, 2, 29);

            final var window = ConversionUtils.calculateConversionAvailablePeriod(purchaseOn(purchaseDate));

            assertEquals(purchaseDate, window.getRight());
            assertEquals(LocalDate.of(2019, 8, 29), window.getLeft());
        }

        @Test
        @DisplayName("clamps to the last valid day when the earlier month is shorter")
        void givenEndOfMonthPurchase_whenCalculatingWindow_thenStartClampsToAValidDate() {
            final var window =
                    ConversionUtils.calculateConversionAvailablePeriod(purchaseOn(LocalDate.of(2024, 8, 31)));

            assertEquals(LocalDate.of(2024, 2, 29), window.getLeft());
        }

        @Test
        @DisplayName("crosses the year boundary correctly")
        void givenEarlyYearPurchase_whenCalculatingWindow_thenStartFallsInThePreviousYear() {
            final var window =
                    ConversionUtils.calculateConversionAvailablePeriod(purchaseOn(LocalDate.of(2024, 3, 10)));

            assertEquals(LocalDate.of(2023, 9, 10), window.getLeft());
        }
    }

    private static PurchaseJpaEntity purchaseOf(final String amount) {
        return PurchaseJpaEntity.newPurchase("A purchase", LocalDate.of(2024, 1, 31), new BigDecimal(amount));
    }

    private static PurchaseJpaEntity purchaseOn(final LocalDate purchaseDate) {
        return PurchaseJpaEntity.newPurchase("A purchase", purchaseDate, new BigDecimal("1.00"));
    }
}
