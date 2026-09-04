package com.pedrolima.wexchange.adapter.out.persistence;

import com.pedrolima.wexchange.adapter.in.web.AbstractPostgresApplicationIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end against a real Postgres and the real application context (issue
 * #17): proves {@link PurchaseRetentionService} - wired with the production
 * {@link java.time.Clock} bean, not a fixed test double - actually deletes a
 * purchase past its retention period and leaves a recent one alone. The
 * scheduled trigger itself is not exercised (waiting a real hour for
 * {@code cleanupInterval} is not practical for a test); calling the service
 * directly proves the same query and wiring the scheduler would invoke.
 */
class PurchaseRetentionIT extends AbstractPostgresApplicationIT {

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private PurchaseRetentionService purchaseRetentionService;

    @Test
    void givenAPurchaseOlderThanTheRetentionPeriod_whenCleaningUp_thenItIsDeletedButARecentOneSurvives() {
        final Instant now = Instant.now();
        final String oldId = "retention-old-" + now.toEpochMilli();
        final String recentId = "retention-recent-" + now.toEpochMilli();

        purchaseRepository.saveAndFlush(PurchaseJpaEntity.newPurchase(
                oldId, "Old purchase", LocalDate.of(2020, 1, 1), new BigDecimal("10.00"), now.minus(120, ChronoUnit.DAYS)));
        purchaseRepository.saveAndFlush(PurchaseJpaEntity.newPurchase(
                recentId, "Recent purchase", LocalDate.of(2024, 1, 1), new BigDecimal("10.00"), now.minus(1, ChronoUnit.DAYS)));

        purchaseRetentionService.deleteExpiredPurchases();

        assertFalse(purchaseRepository.findById(oldId).isPresent(), "a purchase past the retention period must be deleted");
        assertTrue(purchaseRepository.findById(recentId).isPresent(), "a recent purchase must survive cleanup");
    }
}
