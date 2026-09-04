package com.pedrolima.wexchange.adapter.out.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
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
 * rows are the same record. A broken comparison here shows up as duplicated or
 * silently discarded rates.
 */
class EntityIdentityTest {

    private static final String A_PURCHASE_ID = "7d4e1b90-5c3a-4f28-b6d1-08a2e9f5c7b3";

    private static final Instant A_FIXED_INSTANT = Instant.parse("2024-01-31T12:00:00Z");

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
        @DisplayName("identical details under different identifiers are still different records")
        void givenIdenticalDetailsDifferentIds_whenComparing_thenPurchasesDiffer() {
            assertNotEquals(purchaseWithId("a1b2c3d4-0000-4000-8000-000000000001"),
                    purchaseWithId("a1b2c3d4-0000-4000-8000-000000000002"));
        }

        @Test
        @DisplayName("the same identifier is the same record even when the details differ")
        void givenSameIdDifferentDetails_whenComparing_thenPurchasesAreEqual() {
            final var first = PurchaseJpaEntity.newPurchase(
                    A_PURCHASE_ID, "A purchase", LocalDate.of(2024, 1, 31), new BigDecimal("10.00"), A_FIXED_INSTANT);
            final var second = PurchaseJpaEntity.newPurchase(
                    A_PURCHASE_ID, "Another purchase", LocalDate.of(2023, 6, 1), new BigDecimal("99.99"),
                    A_FIXED_INSTANT);

            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
        }

        @Test
        @DisplayName("is never equal to null or to an unrelated type")
        void givenForeignValue_whenComparing_thenNotEqual() {
            final var purchase = newPurchase();

            assertFalse(purchase.equals(null));
            assertFalse(purchase.equals("Brazil-Real"));
        }

        private PurchaseJpaEntity newPurchase() {
            return purchaseWithId(A_PURCHASE_ID);
        }

        private PurchaseJpaEntity purchaseWithId(final String id) {
            return PurchaseJpaEntity.newPurchase(
                    id, "A purchase", LocalDate.of(2024, 1, 31), new BigDecimal("10.00"), A_FIXED_INSTANT);
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
            return CountryCurrencyJpaEntity.of(descriptor, country, currency);
        }
    }

    @Nested
    @DisplayName("idempotency-key identity")
    class IdempotencyKeyIdentity {

        @Test
        @DisplayName("is defined by the key alone, not by fingerprint or status")
        void givenSameKey_whenComparing_thenRecordsAreEqual() {
            final var first = IdempotencyKeyJpaEntity.newClaim(
                    "a-key", "fingerprint-a", A_FIXED_INSTANT, A_FIXED_INSTANT.plusSeconds(60));
            final var second = IdempotencyKeyJpaEntity.newClaim(
                    "a-key", "fingerprint-b", A_FIXED_INSTANT.plusSeconds(1), A_FIXED_INSTANT.plusSeconds(120));

            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
        }

        @Test
        @DisplayName("a record equals itself")
        void givenSameInstance_whenComparing_thenItIsEqual() {
            final var record = IdempotencyKeyJpaEntity.newClaim(
                    "a-key", "fingerprint", A_FIXED_INSTANT, A_FIXED_INSTANT.plusSeconds(60));

            assertEquals(record, record);
        }

        @Test
        @DisplayName("different keys are different records")
        void givenDifferentKeys_whenComparing_thenRecordsDiffer() {
            final var first = IdempotencyKeyJpaEntity.newClaim(
                    "key-1", "fingerprint", A_FIXED_INSTANT, A_FIXED_INSTANT.plusSeconds(60));
            final var second = IdempotencyKeyJpaEntity.newClaim(
                    "key-2", "fingerprint", A_FIXED_INSTANT, A_FIXED_INSTANT.plusSeconds(60));

            assertNotEquals(first, second);
        }

        @Test
        @DisplayName("is never equal to null or to an unrelated type")
        void givenForeignValue_whenComparing_thenNotEqual() {
            final var record = IdempotencyKeyJpaEntity.newClaim(
                    "a-key", "fingerprint", A_FIXED_INSTANT, A_FIXED_INSTANT.plusSeconds(60));

            assertFalse(record.equals(null));
            assertFalse(record.equals("a-key"));
        }
    }

    @Nested
    @DisplayName("country-currency sync run identity")
    class CountryCurrencySyncRunIdentity {

        @Test
        @DisplayName("is always the same singleton record, regardless of status or timestamps")
        void givenTwoRunsWithDifferentDetails_whenComparing_thenTheyAreEqual() {
            final var first = CountryCurrencySyncRunJpaEntity.running(A_FIXED_INSTANT, java.util.Optional.empty());
            final var second = CountryCurrencySyncRunJpaEntity.succeeded(
                    A_FIXED_INSTANT, A_FIXED_INSTANT.plusSeconds(5), java.util.Optional.empty());

            assertEquals(first, second);
            assertEquals(first.hashCode(), second.hashCode());
        }

        @Test
        @DisplayName("a run equals itself")
        void givenSameInstance_whenComparing_thenItIsEqual() {
            final var run = CountryCurrencySyncRunJpaEntity.running(A_FIXED_INSTANT, java.util.Optional.empty());

            assertEquals(run, run);
        }

        @Test
        @DisplayName("is never equal to null or to an unrelated type")
        void givenForeignValue_whenComparing_thenNotEqual() {
            final var run = CountryCurrencySyncRunJpaEntity.running(A_FIXED_INSTANT, java.util.Optional.empty());

            assertFalse(run.equals(null));
            assertFalse(run.equals("singleton"));
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
