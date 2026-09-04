package com.pedrolima.wexchange.adapter.out.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * How long an anonymous purchase is kept before a scheduled job deletes it,
 * and how often that job runs (issue #17). There is no account to reclaim
 * data for, so retention is purely a storage/privacy hygiene decision, not a
 * business rule - see ADR 0004 for why the default is what it is.
 */
@ConfigurationProperties(prefix = "app.purchase-retention")
public record PurchaseRetentionProperties(Duration retention, Duration cleanupInterval) {

    public PurchaseRetentionProperties {
        retention = defaultIfNull(retention, Duration.ofDays(90));
        cleanupInterval = defaultIfNull(cleanupInterval, Duration.ofHours(1));
    }

    private static Duration defaultIfNull(final Duration value, final Duration fallback) {
        return value != null ? value : fallback;
    }
}
