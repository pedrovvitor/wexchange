package com.pedrolima.wexchange.entities;

import com.pedrolima.wexchange.integration.fiscal.beans.CountryCurrencyInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence identity semantics.
 *
 * <p>These are not ceremonial equals/hashCode tests. {@link ExchangeRateCompositeKey}
 * is a JPA {@code @IdClass}: Hibernate uses its equality to decide whether two
 * rows are the same record, and the exchange-rate deduplication in
 * {@code ExchangeRateService} relies on the entity's identity contract. A broken
 * comparison here shows up as duplicated or silently discarded rates.
 */
class EntityIdentityTest {

    @Nested
    @DisplayName("purchase identity")
    class PurchaseIdentity {

        @Test
        @DisplayName("is defined by the identifier alone, not by the purchase details")
        void givenSameId_whenComparing_thenPurchasesAreEqual() {
            final var purchase = newPurchase();

            assertEquals(purchase, purchase);
            assertEquals(purchase.hashCode(), purchase.hashCode());
        }

        @Test
        @DisplayName("two separately created purchases are distinct even with identical details")
        void givenIdenticalDetails_whenComparing_thenPurchasesDiffer() {
            assertNotEquals(newPurchase(), newPurchase());
        }

        @Test
        @DisplayName("is never equal to null or to an unrelated type")
        void givenForeignValue_whenComparing_thenNotEqual() {
            final var purchase = newPurchase();

            assertFalse(purchase.equals(null));
            assertFalse(purchase.equals("Brazil-Real"));
        }

        private PurchaseJpaEntity newPurchase() {
            return PurchaseJpaEntity.newPurchase("A purchase", LocalDate.of(2024, 1, 31), new BigDecimal("10.00"));
        }
    }

    @Nested
    @DisplayName("country-currency identity")
    class CountryCurrencyIdentity {

        @Test
        @DisplayName("is defined by the country-currency descriptor")
        void givenSameDescriptor_whenComparing_thenEntitiesAreEqual() {
            final var first = entity("Brazil-Real", "Brazil", "Real");
            final var second = entity("Brazil-Real", "Brazil", "Real");

            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
        }

        @Test
        @DisplayName("ignores the country and currency columns when comparing")
        void givenSameDescriptorDifferentColumns_whenComparing_thenEntitiesAreEqual() {
            assertEquals(entity("Brazil-Real", "Brazil", "Real"), entity("Brazil-Real", "Brasil", "BRL"));
        }

        @Test
        @DisplayName("different descriptors are different records")
        void givenDifferentDescriptor_whenComparing_thenEntitiesDiffer() {
            assertNotEquals(entity("Brazil-Real", "Brazil", "Real"), entity("Canada-Dollar", "Canada", "Dollar"));
        }

        @Test
        @DisplayName("is never equal to null or to an unrelated type")
        void givenForeignValue_whenComparing_thenNotEqual() {
            final var entity = entity("Brazil-Real", "Brazil", "Real");

            assertFalse(entity.equals(null));
            assertFalse(entity.equals("Brazil-Real"));
        }

        private CountryCurrencyJpaEntity entity(final String descriptor, final String country, final String currency) {
            return CountryCurrencyJpaEntity.with(new CountryCurrencyInput(descriptor, country, currency));
        }
    }

    @Nested
    @DisplayName("exchange-rate composite key")
    class ExchangeRateKey {

        @Test
        @DisplayName("a default-constructed key equals another default-constructed key")
        void givenTwoEmptyKeys_whenComparing_thenTheyAreEqual() {
            final var first = new ExchangeRateCompositeKey();
            final var second = new ExchangeRateCompositeKey();

            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
        }

        @Test
        @DisplayName("a key equals itself")
        void givenSameInstance_whenComparing_thenItIsEqual() {
            final var key = new ExchangeRateCompositeKey();

            assertTrue(key.equals(key));
        }

        @Test
        @DisplayName("is never equal to null or to an unrelated type")
        void givenForeignValue_whenComparing_thenNotEqual() {
            final var key = new ExchangeRateCompositeKey();

            assertFalse(key.equals(null));
            assertFalse(key.equals("Brazil-Real|2024-01-31"));
        }

        @Test
        @DisplayName("exposes the country-currency and effective-date components it compares on")
        void givenDefaultKey_whenReadingComponents_thenBothAreExposed() {
            final var key = new ExchangeRateCompositeKey();

            assertEquals(null, key.getCountryCurrency());
            assertEquals(null, key.getEffectiveDate());
        }
    }
}
