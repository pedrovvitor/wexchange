package com.pedrolima.wexchange.application;

import com.pedrolima.wexchange.application.port.PurchaseStore;
import com.pedrolima.wexchange.domain.error.ResourceNotFoundException;
import com.pedrolima.wexchange.domain.money.Money;
import com.pedrolima.wexchange.domain.purchase.Purchase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPurchaseServiceTest {

    private static final String ID = "3e4f8a12-6b9d-4c1e-8f7a-2d5c9e0b1a3f";

    @Mock
    private PurchaseStore purchases;

    private GetPurchaseService service;

    @BeforeEach
    void setUp() {
        service = new GetPurchaseService(purchases);
    }

    @Test
    @DisplayName("returns the stored purchase when it exists")
    void givenStoredPurchase_whenGetting_thenItIsReturned() {
        final var purchase = Purchase.create(
                ID, "A purchase", LocalDate.of(2024, 1, 31), Money.of("10.00"), Instant.parse("2024-01-31T12:00:00Z"));
        when(purchases.findById(ID)).thenReturn(Optional.of(purchase));

        assertEquals(purchase, service.execute(ID));
    }

    @Test
    @DisplayName("reports a missing purchase rather than returning null")
    void givenNoPurchase_whenGetting_thenItIsReported() {
        when(purchases.findById(ID)).thenReturn(Optional.empty());

        final var thrown = assertThrows(ResourceNotFoundException.class, () -> service.execute(ID));

        assertEquals("Purchase not found for id: " + ID, thrown.getMessage());
    }
}
