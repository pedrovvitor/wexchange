package com.pedrolima.wexchange.entities;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PurchaseTest {

    @Test
    void givenValidParams_whenCallNewPurchase_thenInstantiateAPurchase() {
        final var description = "Test Purchase";
        final var date = LocalDate.now();
        final var amount = BigDecimal.valueOf(100);

        Purchase purchase = Purchase.newPurchase(description, date, amount);

        assertNotNull(purchase.getId());
        assertEquals(description, purchase.getDescription());
        assertEquals(date, purchase.getDate());
        assertEquals(amount, purchase.getAmount());
        assertNotNull(purchase.getCreatedAt());
        assertNotNull(purchase.getUpdatedAt());
    }
}
