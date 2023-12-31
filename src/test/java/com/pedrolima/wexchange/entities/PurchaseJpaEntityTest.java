package com.pedrolima.wexchange.entities;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PurchaseJpaEntityTest {

    @Test
    void givenValidParams_whenCallNewPurchase_thenInstantiateAPurchase() {
        final var aDescription = "Test Purchase";
        final var aDate = LocalDate.now();
        final var anAmount = BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_EVEN);

        PurchaseJpaEntity purchase = PurchaseJpaEntity.newPurchase(aDescription, aDate, anAmount);

        assertNotNull(purchase.getId());
        assertEquals(aDescription, purchase.getDescription());
        assertEquals(aDate, purchase.getPurchaseDate());
        assertEquals(anAmount, purchase.getAmount());
        assertNotNull(purchase.getCreatedAt());
        assertNotNull(purchase.getUpdatedAt());
    }
}
