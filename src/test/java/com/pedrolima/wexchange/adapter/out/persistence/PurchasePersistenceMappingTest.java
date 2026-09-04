package com.pedrolima.wexchange.adapter.out.persistence;

import com.pedrolima.wexchange.domain.money.Money;
import com.pedrolima.wexchange.domain.purchase.Purchase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mapping between the domain and the persistence model, in both directions.
 *
 * <p>These fields are same-typed and adjacent, so a transposed argument compiles
 * happily and only shows up as a purchase with the wrong description or a rate
 * against the wrong currency. A silent mapping bug is the failure this boundary
 * makes possible, so it is the one this suite exists to catch.
 */
class PurchasePersistenceMappingTest {

    private static final String ID = "4a7c2e91-8d35-4b06-9f1a-63b8e0d5c74f";

    private static final LocalDate DATE = LocalDate.of(2024, 7, 15);

    private static final Instant CREATED = Instant.parse("2024-07-15T12:00:00Z");

    private static final Instant UPDATED = Instant.parse("2024-08-01T09:30:00Z");

    @Test
    @DisplayName("domain to entity keeps every field in its own slot")
    void givenPurchase_whenMappingToEntity_thenFieldsAreNotTransposed() {
        final var purchase = Purchase.restore(ID, "A purchase", DATE, Money.of("10.00"), CREATED, UPDATED);

        final var entity = PurchaseJpaEntity.fromDomain(purchase);

        assertEquals(ID, entity.getId());
        assertEquals("A purchase", entity.getDescription());
        assertEquals(DATE, entity.getPurchaseDate());
        assertEquals(new BigDecimal("10.00"), entity.getAmount());
        assertEquals(CREATED, entity.getCreatedAt());
        assertEquals(UPDATED, entity.getUpdatedAt());
    }

    @Test
    @DisplayName("entity to domain keeps every field in its own slot")
    void givenEntity_whenMappingToDomain_thenFieldsAreNotTransposed() {
        final var entity = PurchaseJpaEntity.newPurchase(ID, "A purchase", DATE, new BigDecimal("10.00"), CREATED);

        final var purchase = entity.toDomain();

        assertEquals(ID, purchase.id());
        assertEquals("A purchase", purchase.description());
        assertEquals(DATE, purchase.purchaseDate());
        assertEquals(Money.of("10.00"), purchase.amount());
        assertEquals(CREATED, purchase.createdAt());
        assertEquals(CREATED, purchase.updatedAt());
    }

    @Test
    @DisplayName("a round trip through persistence loses nothing")
    void givenPurchase_whenRoundTripping_thenItIsUnchanged() {
        final var purchase = Purchase.restore(ID, "A purchase", DATE, Money.of("1234.56"), CREATED, UPDATED);

        assertEquals(purchase, PurchaseJpaEntity.fromDomain(purchase).toDomain());
    }

    @Test
    @DisplayName("the column's scale is applied on the way in, not silently on the way out")
    void givenUnscaledAmount_whenRoundTripping_thenScaleIsNormalisedOnce() {
        final var purchase = Purchase.restore(ID, "A purchase", DATE, Money.of("10.005"), CREATED, UPDATED);

        // 10.005 rounds half-even to 10.00 in Money, and the column stores that.
        assertEquals(new BigDecimal("10.00"), PurchaseJpaEntity.fromDomain(purchase).getAmount());
        assertEquals(Money.of("10.00"), PurchaseJpaEntity.fromDomain(purchase).toDomain().amount());
    }

    @Test
    @DisplayName("an exchange-rate row maps to its domain rate without losing precision")
    void givenRateEntity_whenMappingToDomain_thenPrecisionSurvives() {
        final var entity = ExchangeRateJpaEntity.newConversionRate(
                "Brazil-Real", DATE, new BigDecimal("4.925"));

        final var rate = entity.toDomain();

        assertEquals("Brazil-Real", rate.countryCurrency());
        assertEquals(DATE, rate.effectiveDate());
        assertEquals(new BigDecimal("4.925"), rate.rateValue());
    }
}
