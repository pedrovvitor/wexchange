package com.pedrolima.wexchange.domain.exchange;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The conversion window is the second financial invariant, and a mutation-testing
 * target for the same reason as {@code Money}.
 */
class ConversionWindowTest {

    @Test
    @DisplayName("spans exactly the six months preceding the purchase date, inclusive of it")
    void givenPurchaseDate_whenBuildingWindow_thenItStartsSixMonthsEarlier() {
        final var purchaseDate = LocalDate.of(2024, 7, 15);

        final var window = ConversionWindow.endingOn(purchaseDate);

        assertEquals(LocalDate.of(2024, 1, 15), window.start());
        assertEquals(purchaseDate, window.end());
    }

    @Test
    @DisplayName("the window ends on the purchase date, never on today")
    void givenPastPurchase_whenBuildingWindow_thenUpperBoundIsThePurchaseDate() {
        final var window = ConversionWindow.endingOn(LocalDate.of(2020, 2, 29));

        assertEquals(LocalDate.of(2020, 2, 29), window.end());
        assertEquals(LocalDate.of(2019, 8, 29), window.start());
    }

    @Test
    @DisplayName("clamps to the last valid day when the earlier month is shorter")
    void givenEndOfMonthPurchase_whenBuildingWindow_thenStartClampsToAValidDate() {
        assertEquals(LocalDate.of(2024, 2, 29), ConversionWindow.endingOn(LocalDate.of(2024, 8, 31)).start());
    }

    @Test
    @DisplayName("crosses the year boundary correctly")
    void givenEarlyYearPurchase_whenBuildingWindow_thenStartFallsInThePreviousYear() {
        assertEquals(LocalDate.of(2023, 9, 10), ConversionWindow.endingOn(LocalDate.of(2024, 3, 10)).start());
    }

    @Test
    @DisplayName("a null purchase date is rejected rather than carried")
    void givenNullDate_whenBuildingWindow_thenItIsRejected() {
        assertThrows(NullPointerException.class, () -> ConversionWindow.endingOn(null));
    }
}
