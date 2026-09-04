package com.pedrolima.wexchange.adapter.out.persistence;

import com.pedrolima.wexchange.domain.money.Money;
import com.pedrolima.wexchange.domain.purchase.Purchase;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Constraints and precision that only a real engine enforces (issue #5):
 * primary-key uniqueness, and that PostgreSQL round-trips the exact decimal
 * scale and instant precision the entities declare. {@code save()} on an
 * entity with an application-assigned id merges rather than inserting, so a
 * duplicate-key violation can only be forced through direct
 * {@link TestEntityManager#persist} plus an explicit flush.
 */
class PersistenceConstraintsIT extends AbstractPostgresRepositoryIT {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private CountryCurrencyRepository countryCurrencyRepository;

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    /*
     * Each test flushes the first row and clears the session before the second
     * persist: otherwise Hibernate's own first-level cache would reject the
     * duplicate id from its in-memory identity map before ever reaching the
     * database, which would prove Hibernate's bookkeeping works but not that
     * PostgreSQL's own constraint does.
     */

    @Test
    void givenTwoPurchasesWithTheSameId_whenPersistingBoth_thenThePrimaryKeyIsEnforced() {
        final var first = PurchaseJpaEntity.newPurchase(
                "dup-id", "First", LocalDate.of(2024, 1, 1), new BigDecimal("10.00"), Instant.now());
        entityManager.persistAndFlush(first);
        entityManager.clear();

        final var second = PurchaseJpaEntity.newPurchase(
                "dup-id", "Second", LocalDate.of(2024, 1, 2), new BigDecimal("20.00"), Instant.now());
        assertThrows(PersistenceException.class, () -> entityManager.persistAndFlush(second));
    }

    @Test
    void givenTwoCountryCurrenciesWithTheSameKey_whenPersistingBoth_thenThePrimaryKeyIsEnforced() {
        entityManager.persistAndFlush(CountryCurrencyJpaEntity.of("Brazil-Real", "Brazil", "Real"));
        entityManager.clear();

        assertThrows(PersistenceException.class, () -> entityManager.persistAndFlush(
                CountryCurrencyJpaEntity.of("Brazil-Real", "Brazil", "Real (duplicate)")));
    }

    @Test
    void givenTwoExchangeRatesWithTheSameCompositeKey_whenPersistingBoth_thenThePrimaryKeyIsEnforced() {
        final var effectiveDate = LocalDate.of(2024, 1, 1);
        entityManager.persistAndFlush(ExchangeRateJpaEntity.newConversionRate("Brazil-Real", effectiveDate, new BigDecimal("5.220")));
        entityManager.clear();

        assertThrows(PersistenceException.class, () -> entityManager.persistAndFlush(
                ExchangeRateJpaEntity.newConversionRate("Brazil-Real", effectiveDate, new BigDecimal("5.230"))));
    }

    @Test
    void givenAStoredCompositeKeyRow_whenLookedUpByAnIndependentlyConstructedKey_thenItIsFound() {
        final var effectiveDate = LocalDate.of(2024, 3, 15);
        exchangeRateRepository.save(ExchangeRateJpaEntity.newConversionRate("Japan-Yen", effectiveDate, new BigDecimal("150.500")));

        final Optional<ExchangeRateJpaEntity> found =
                exchangeRateRepository.findById(new ExchangeRateCompositeKey("Japan-Yen", effectiveDate));

        assertTrue(found.isPresent());
        assertEquals(new BigDecimal("150.500"), found.get().getRateValue());
    }

    @Test
    void givenAStoredCompositeKeyRow_whenLookedUpByADifferentDate_thenItIsNotFound() {
        exchangeRateRepository.save(
                ExchangeRateJpaEntity.newConversionRate("Japan-Yen", LocalDate.of(2024, 3, 15), new BigDecimal("150.500")));

        final Optional<ExchangeRateJpaEntity> found = exchangeRateRepository.findById(
                new ExchangeRateCompositeKey("Japan-Yen", LocalDate.of(2024, 3, 16)));

        assertTrue(found.isEmpty());
    }

    @Test
    void givenAPurchase_whenSavedAndReloaded_thenTheAmountScaleAndInstantPrecisionSurviveExactly() {
        final var createdAt = Instant.parse("2024-06-15T10:30:00.123456Z");
        final var purchase = Purchase.restore(
                "precision-check", "Precision check", LocalDate.of(2024, 6, 15),
                new Money(new BigDecimal("1234.50")), createdAt, createdAt);
        purchaseRepository.saveAndFlush(PurchaseJpaEntity.fromDomain(purchase));
        entityManager.clear();

        final var reloaded = purchaseRepository.findById("precision-check").orElseThrow();

        assertEquals(new BigDecimal("1234.50"), reloaded.getAmount());
        assertEquals(createdAt, reloaded.getCreatedAt());
    }

    @Test
    void givenAnExchangeRate_whenSavedAndReloaded_thenTheThreeDecimalScaleSurvivesExactly() {
        exchangeRateRepository.saveAndFlush(
                ExchangeRateJpaEntity.newConversionRate("Precision-Coin", LocalDate.of(2024, 1, 1), new BigDecimal("1.005")));
        entityManager.clear();

        final var reloaded = exchangeRateRepository
                .findById(new ExchangeRateCompositeKey("Precision-Coin", LocalDate.of(2024, 1, 1)))
                .orElseThrow();

        assertEquals(new BigDecimal("1.005"), reloaded.getRateValue());
    }

    @Test
    void givenNoStoredCountryCurrency_whenCheckingExistence_thenItReportsNotExisting() {
        assertTrue(countryCurrencyRepository.notExistsByCountryCurrency("Never-Stored"));
    }
}
