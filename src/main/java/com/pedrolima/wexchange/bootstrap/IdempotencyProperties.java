package com.pedrolima.wexchange.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Purchase-creation idempotency knobs (issue #18): how long a completed or
 * abandoned claim is kept before it may be reclaimed or is no longer honored,
 * and how long - and how often - a request waits for a concurrent, identical
 * request to finish before it is told to retry.
 */
@ConfigurationProperties(prefix = "app.idempotency")
public record IdempotencyProperties(Duration retention, Duration maxWait, Duration pollInterval) {

    public IdempotencyProperties {
        retention = defaultIfNull(retention, Duration.ofHours(24));
        maxWait = defaultIfNull(maxWait, Duration.ofSeconds(5));
        pollInterval = defaultIfNull(pollInterval, Duration.ofMillis(50));
    }

    private static Duration defaultIfNull(final Duration value, final Duration fallback) {
        return value != null ? value : fallback;
    }
}
