package com.pedrolima.wexchange.entities;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PurchaseJpaEntityTest {

    @Test
    void givenValidParams_whenCallNewPurchase_thenInstantiateAPurchase() {
        final var anId = "3c9a1f7e-2b84-4d51-8a6f-91e0c7d4b2a5";
        final var aDescription = "Test Purchase";
        final var aDate = LocalDate.of(2024, 1, 31);
        final var anAmount = BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_EVEN);
        final var now = Instant.parse("2024-01-31T12:00:00Z");

        PurchaseJpaEntity purchase = PurchaseJpaEntity.newPurchase(anId, aDescription, aDate, anAmount, now);

        assertEquals(anId, purchase.getId());
        assertEquals(aDescription, purchase.getDescription());
        assertEquals(aDate, purchase.getPurchaseDate());
        assertEquals(anAmount, purchase.getAmount());
        assertEquals(now, purchase.getCreatedAt());
        assertEquals(now, purchase.getUpdatedAt());
    }
}
