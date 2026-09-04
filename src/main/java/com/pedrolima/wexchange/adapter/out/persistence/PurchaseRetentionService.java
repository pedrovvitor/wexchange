package com.pedrolima.wexchange.adapter.out.persistence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Deletes anonymous purchases past their retention period (issue #17). There
 * is no account to reclaim data for, so a purchase outliving both its
 * conversion window (six months) and a generous margin has no remaining
 * purpose - see ADR 0004 for the retention-period decision.
 *
 * <p>Idempotent by construction: a run that finds nothing past the cutoff
 * simply deletes zero rows, and a repeated run over an already-cleaned table
 * behaves identically to the first.
 */
@Service
@Slf4j
@EnableConfigurationProperties(PurchaseRetentionProperties.class)
public class PurchaseRetentionService {

    private final PurchaseRepository repository;

    private final Clock clock;

    private final Duration retention;

    private final PurchaseRetentionMetrics metrics;

    public PurchaseRetentionService(
            final PurchaseRepository repository,
            final Clock clock,
            final PurchaseRetentionProperties properties,
            final PurchaseRetentionMetrics metrics
    ) {
        this.repository = repository;
        this.clock = clock;
        this.retention = properties.retention();
        this.metrics = metrics;
    }

    @Scheduled(
            fixedDelayString = "${app.purchase-retention.cleanup-interval}",
            initialDelayString = "${app.purchase-retention.cleanup-interval}"
    )
    @Transactional
    public void deleteExpiredPurchases() {
        final Instant cutoff = clock.instant().minus(retention);
        final long deleted = repository.deleteByCreatedAtBefore(cutoff);
        metrics.recordDeleted(deleted);
        if (deleted > 0) {
            log.info("Deleted {} purchase(s) created before {}", deleted, cutoff);
        }
    }
}
