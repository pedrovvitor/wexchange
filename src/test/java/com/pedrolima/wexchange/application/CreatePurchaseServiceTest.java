package com.pedrolima.wexchange.application;

import com.pedrolima.wexchange.application.port.ExchangeRateRefresher;
import com.pedrolima.wexchange.application.port.PurchaseStore;
import com.pedrolima.wexchange.domain.money.Money;
import com.pedrolima.wexchange.domain.purchase.Purchase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The use case owns two decisions that used to be ambient: which identifier a
 * purchase gets and what "now" means. Both are injected, so these tests assert
 * the exact values reaching storage instead of asserting around a random UUID
 * and a wall clock. No Spring context is involved: the service is a plain class.
 */
@ExtendWith(MockitoExtension.class)
class CreatePurchaseServiceTest {

    private static final String ID = "2f8c6d10-9a4b-4e73-85cf-13d0b7e6a924";

    private static final Instant NOW = Instant.parse("2024-01-31T12:00:00Z");

    private static final LocalDate DATE = LocalDate.of(2024, 1, 31);

    @Mock
    private PurchaseStore purchases;

    @Mock
    private ExchangeRateRefresher rateRefresher;

    private CreatePurchaseService service;

    @BeforeEach
    void setUp() {
        service = new CreatePurchaseService(
                purchases, rateRefresher, () -> ID, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("the stored purchase carries the generated identifier and the clock's instant")
    void givenValidInput_whenCreating_thenIdentityAndTimestampsComeFromTheCollaborators() {
        when(purchases.save(any(Purchase.class))).thenAnswer(call -> call.getArgument(0));
        when(purchases.countByPurchaseDate(DATE)).thenReturn(2L);

        service.execute("Test Purchase", DATE, new BigDecimal("100.0"));

        final var saved = ArgumentCaptor.forClass(Purchase.class);
        verify(purchases).save(saved.capture());

        assertEquals(ID, saved.getValue().id());
        assertEquals(NOW, saved.getValue().createdAt());
        assertEquals(NOW, saved.getValue().updatedAt());
        assertEquals(Money.of("100.00"), saved.getValue().amount());
    }

    @Test
    @DisplayName("the first purchase of a date warms the rate cache for its window")
    void givenFirstPurchaseOfTheDate_whenCreating_thenRatesAreRefreshed() {
        final var stored = Purchase.create(ID, "Test Purchase", DATE, Money.of("100.00"), NOW);
        when(purchases.save(any(Purchase.class))).thenReturn(stored);
        when(purchases.countByPurchaseDate(DATE)).thenReturn(1L);

        service.execute("Test Purchase", DATE, new BigDecimal("100.00"));

        verify(rateRefresher).refreshFor(stored);
    }

    @Test
    @DisplayName("a later purchase on a covered date does not refresh again")
    void givenLaterPurchaseOfTheDate_whenCreating_thenRatesAreNotRefreshed() {
        when(purchases.save(any(Purchase.class))).thenAnswer(call -> call.getArgument(0));
        when(purchases.countByPurchaseDate(DATE)).thenReturn(2L);

        service.execute("Test Purchase", DATE, new BigDecimal("100.00"));

        verify(rateRefresher, never()).refreshFor(any());
    }

    @Test
    @DisplayName("what storage returns is what the caller gets, not the instance passed in")
    void givenStorageAssignsValues_whenCreating_thenTheStoredPurchaseIsReturned() {
        final var stored = Purchase.create(ID, "As stored", DATE, Money.of("100.00"), NOW);
        when(purchases.save(any(Purchase.class))).thenReturn(stored);
        when(purchases.countByPurchaseDate(DATE)).thenReturn(2L);

        assertEquals(stored, service.execute("Test Purchase", DATE, new BigDecimal("100.00")));
    }
}
