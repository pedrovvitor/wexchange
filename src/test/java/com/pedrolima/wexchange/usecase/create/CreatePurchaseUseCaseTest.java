package com.pedrolima.wexchange.usecase.create;

import com.pedrolima.wexchange.entities.PurchaseJpaEntity;
import com.pedrolima.wexchange.purchase.models.CreatePurchaseApiInput;
import com.pedrolima.wexchange.purchase.models.CreatePurchaseApiOutput;
import com.pedrolima.wexchange.repositories.PurchaseRepository;
import com.pedrolima.wexchange.services.async.ExchangeRateService;
import com.pedrolima.wexchange.usecases.purchase.create.DefaultCreatePurchaseUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The use case owns the two decisions that used to be ambient: which identifier
 * a new purchase gets, and what "now" means. Both are injected, so these tests
 * assert the exact values that reach the repository instead of asserting around
 * a random UUID and a wall clock.
 */
@ExtendWith(MockitoExtension.class)
public class CreatePurchaseUseCaseTest {

    private static final String A_PURCHASE_ID = "2f8c6d10-9a4b-4e73-85cf-13d0b7e6a924";

    private static final Instant A_FIXED_INSTANT = Instant.parse("2024-01-31T12:00:00Z");

    private static final LocalDate A_PURCHASE_DATE = LocalDate.of(2024, 1, 31);

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private ExchangeRateService exchangeRateService;

    private DefaultCreatePurchaseUseCase createPurchaseUseCase;

    @BeforeEach
    void setUp() {
        createPurchaseUseCase = new DefaultCreatePurchaseUseCase(
                purchaseRepository,
                exchangeRateService,
                () -> A_PURCHASE_ID,
                Clock.fixed(A_FIXED_INSTANT, ZoneOffset.UTC));
    }

    @Test
    void givenValidFirstPurchaseOfTheDate_whenCreatePurchase_thenPersistPurchaseAndUpdateExchangeRatesAndReturnDetails() {
        final var description = "Test Purchase";
        final var amount = BigDecimal.valueOf(100.0).setScale(2, RoundingMode.HALF_EVEN);
        final var input = new CreatePurchaseApiInput(description, A_PURCHASE_DATE, amount);

        final var expectedPurchase = aPurchase(description, amount);
        when(purchaseRepository.save(any(PurchaseJpaEntity.class))).thenReturn(expectedPurchase);
        Mockito.doNothing().when(exchangeRateService).updateExchangeRates(expectedPurchase);
        when(purchaseRepository.countByPurchaseDate(A_PURCHASE_DATE)).thenReturn(1L);

        CreatePurchaseApiOutput result = createPurchaseUseCase.execute(input);

        verify(purchaseRepository).save(any(PurchaseJpaEntity.class));
        verify(exchangeRateService).updateExchangeRates(any(PurchaseJpaEntity.class));
        assertEquals(description, result.description());
        assertEquals(A_PURCHASE_DATE.toString(), result.date());
        assertEquals(amount, result.amount());
        assertFalse(result.links().isEmpty());
    }

    @Test
    void givenNotFirstPurchaseOfTheDate_whenCreatePurchase_thenPersistPurchaseWithoutUpdatingExchangeRatesAndReturnDetails() {
        final var description = "Test Purchase";
        final var amount = BigDecimal.valueOf(100.0).setScale(2, RoundingMode.HALF_EVEN);
        final var input = new CreatePurchaseApiInput(description, A_PURCHASE_DATE, amount);

        when(purchaseRepository.save(any(PurchaseJpaEntity.class))).thenReturn(aPurchase(description, amount));
        when(purchaseRepository.countByPurchaseDate(A_PURCHASE_DATE)).thenReturn(2L);

        CreatePurchaseApiOutput result = createPurchaseUseCase.execute(input);

        verify(purchaseRepository).save(any(PurchaseJpaEntity.class));
        verify(exchangeRateService, never()).updateExchangeRates(any(PurchaseJpaEntity.class));
        assertEquals(description, result.description());
        assertEquals(A_PURCHASE_DATE.toString(), result.date());
        assertEquals(amount, result.amount());
        assertFalse(result.links().isEmpty());
    }

    @Test
    @DisplayName("the persisted purchase carries the generated identifier and the injected clock's instant")
    void givenValidPurchase_whenCreatePurchase_thenIdentityAndTimestampsComeFromTheInjectedCollaborators() {
        final var amount = new BigDecimal("100.00");
        final var input = new CreatePurchaseApiInput("Test Purchase", A_PURCHASE_DATE, amount);

        when(purchaseRepository.save(any(PurchaseJpaEntity.class)))
                .thenReturn(aPurchase("Test Purchase", amount));
        when(purchaseRepository.countByPurchaseDate(A_PURCHASE_DATE)).thenReturn(2L);

        createPurchaseUseCase.execute(input);

        final var persisted = ArgumentCaptor.forClass(PurchaseJpaEntity.class);
        verify(purchaseRepository).save(persisted.capture());

        assertEquals(A_PURCHASE_ID, persisted.getValue().getId());
        assertEquals(A_FIXED_INSTANT, persisted.getValue().getCreatedAt());
        assertEquals(A_FIXED_INSTANT, persisted.getValue().getUpdatedAt());
    }

    @Test
    @DisplayName("the response advertises the conversion endpoint for the identifier just generated")
    void givenValidPurchase_whenCreatePurchase_thenTheConversionLinkCarriesTheNewIdentifier() {
        final var amount = new BigDecimal("100.00");
        final var input = new CreatePurchaseApiInput("Test Purchase", A_PURCHASE_DATE, amount);

        when(purchaseRepository.save(any(PurchaseJpaEntity.class)))
                .thenReturn(aPurchase("Test Purchase", amount));
        when(purchaseRepository.countByPurchaseDate(A_PURCHASE_DATE)).thenReturn(2L);

        final var links = createPurchaseUseCase.execute(input).links();

        assertEquals(2, links.size());
        assertEquals("convert", links.get(0).rel());
        assertEquals("/v1/purchases/" + A_PURCHASE_ID + "/convert?country_currency=", links.get(0).href());
        assertEquals("country_currencies", links.get(1).rel());
    }

    /**
     * A stand-in for what the repository returns after saving. Declaring the
     * dependency explicitly keeps the type visible to readers of this file.
     */
    private static PurchaseJpaEntity aPurchase(final String description, final BigDecimal amount) {
        return PurchaseJpaEntity.newPurchase(A_PURCHASE_ID, description, A_PURCHASE_DATE, amount, A_FIXED_INSTANT);
    }
}
