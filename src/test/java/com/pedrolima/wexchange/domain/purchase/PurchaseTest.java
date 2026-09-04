package com.pedrolima.wexchange.domain.purchase;

import com.pedrolima.wexchange.domain.exchange.ExchangeRate;
import com.pedrolima.wexchange.domain.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PurchaseTest {

    private static final String ID = "9c1e7b62-45af-4d38-8b70-2ea6f5c39d81";

    private static final LocalDate DATE = LocalDate.of(2024, 7, 15);

    private static final Instant NOW = Instant.parse("2024-07-15T12:00:00Z");

    @Test
    @DisplayName("a new purchase carries the supplied identity and timestamps")
    void givenValidValues_whenCreating_thenIdentityAndTimestampsAreTheOnesGiven() {
        final var purchase = Purchase.create(ID, "A purchase", DATE, Money.of("10.00"), NOW);

        assertEquals(ID, purchase.id());
        assertEquals("A purchase", purchase.description());
        assertEquals(DATE, purchase.purchaseDate());
        assertEquals(Money.of("10.00"), purchase.amount());
        assertEquals(NOW, purchase.createdAt());
        assertEquals(NOW, purchase.updatedAt());
    }

    @ParameterizedTest
    @DisplayName("a blank identifier or description is rejected")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void givenBlankText_whenCreating_thenItIsRejected(final String blank) {
        assertThrows(IllegalArgumentException.class,
                () -> Purchase.create(blank, "A purchase", DATE, Money.of("10.00"), NOW));
        assertThrows(IllegalArgumentException.class,
                () -> Purchase.create(ID, blank, DATE, Money.of("10.00"), NOW));
    }

    @Test
    @DisplayName("a negative amount is rejected, zero is not")
    void givenNegativeAmount_whenCreating_thenItIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Purchase.create(ID, "A purchase", DATE, Money.of("-0.01"), NOW));

        assertEquals(Money.of("0.00"), Purchase.create(ID, "A purchase", DATE, Money.of("0.00"), NOW).amount());
    }

    @Test
    @DisplayName("missing date, amount, or instant is rejected rather than carried")
    void givenNullRequiredValue_whenCreating_thenItIsRejected() {
        assertThrows(NullPointerException.class,
                () -> Purchase.create(ID, "A purchase", null, Money.of("10.00"), NOW));
        assertThrows(NullPointerException.class,
                () -> Purchase.create(ID, "A purchase", DATE, null, NOW));
        assertThrows(NullPointerException.class,
                () -> Purchase.create(ID, "A purchase", DATE, Money.of("10.00"), null));
    }

    @Test
    @DisplayName("a restored purchase keeps distinct created and updated instants")
    void givenStoredValues_whenRestoring_thenBothTimestampsSurvive() {
        final var later = Instant.parse("2024-08-01T09:30:00Z");

        final var purchase = Purchase.restore(ID, "A purchase", DATE, Money.of("10.00"), NOW, later);

        assertEquals(NOW, purchase.createdAt());
        assertEquals(later, purchase.updatedAt());
    }

    @Test
    @DisplayName("restoring does not re-apply the creation invariants")
    void givenRowThatPredatesTheRules_whenRestoring_thenItStillLoads() {
        final var purchase = Purchase.restore(ID, "", DATE, Money.of("-1.00"), NOW, NOW);

        assertEquals("", purchase.description());
    }

    @Test
    @DisplayName("the conversion window is the six months up to the purchase date")
    void givenPurchase_whenAskingForItsWindow_thenItEndsOnThePurchaseDate() {
        final var window = Purchase.create(ID, "A purchase", DATE, Money.of("10.00"), NOW).conversionWindow();

        assertEquals(LocalDate.of(2024, 1, 15), window.start());
        assertEquals(DATE, window.end());
    }

    @Test
    @DisplayName("converting keeps the purchase and the rate alongside the figure")
    void givenRate_whenConverting_thenResultCarriesItsProvenance() {
        final var purchase = Purchase.create(ID, "A purchase", DATE, Money.of("10.00"), NOW);
        final var rate = new ExchangeRate("Brazil-Real", DATE, new BigDecimal("4.925"));

        final var converted = purchase.convertWith(rate);

        assertEquals(purchase, converted.purchase());
        assertEquals(rate, converted.rate());
        assertEquals(Money.of("49.25"), converted.convertedAmount());
    }

    @Test
    @DisplayName("a null rate is rejected rather than producing a null figure")
    void givenNullRate_whenConverting_thenItIsRejected() {
        final var purchase = Purchase.create(ID, "A purchase", DATE, Money.of("10.00"), NOW);

        assertThrows(NullPointerException.class, () -> purchase.convertWith(null));
    }
}
