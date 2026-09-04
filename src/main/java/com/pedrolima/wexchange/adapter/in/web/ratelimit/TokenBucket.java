package com.pedrolima.wexchange.adapter.in.web.ratelimit;

import com.pedrolima.wexchange.adapter.in.web.ratelimit.AbuseControlProperties.RouteLimit;

import java.time.Clock;

/**
 * A single caller's budget for one route (issue #17): up to {@code capacity}
 * requests may be spent immediately (the burst), refilling continuously at
 * {@code refillTokens} per {@code refillPeriod} thereafter. Refill is lazy -
 * computed from elapsed time on each call rather than by a background
 * scheduler - so an idle bucket costs nothing between requests.
 */
final class TokenBucket {

    private final int capacity;
    private final double refillTokensPerNano;
    private final Clock clock;

    private double availableTokens;
    private long lastRefillNanos;

    TokenBucket(final RouteLimit limit, final Clock clock) {
        this.capacity = limit.capacity();
        this.refillTokensPerNano = limit.refillTokens() / (double) limit.refillPeriod().toNanos();
        this.clock = clock;
        this.availableTokens = capacity;
        this.lastRefillNanos = nanos();
    }

    synchronized RateLimitDecision consume() {
        refill();
        if (availableTokens >= 1.0) {
            availableTokens -= 1.0;
            return new RateLimitDecision(true, 0);
        }
        final double missingTokens = 1.0 - availableTokens;
        final double nanosNeeded = missingTokens / refillTokensPerNano;
        final long retryAfterSeconds = (long) Math.ceil(nanosNeeded / 1_000_000_000.0);
        return new RateLimitDecision(false, retryAfterSeconds);
    }

    private void refill() {
        final long now = nanos();
        final long elapsed = now - lastRefillNanos;
        if (elapsed > 0) {
            availableTokens = Math.min(capacity, availableTokens + elapsed * refillTokensPerNano);
            lastRefillNanos = now;
        }
    }

    private long nanos() {
        final var instant = clock.instant();
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }
}
