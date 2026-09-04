package com.pedrolima.wexchange.adapter.in.web.ratelimit;

import com.pedrolima.wexchange.adapter.in.web.ratelimit.AbuseControlProperties.RouteLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Refill is lazy - computed from elapsed clock time on each call - so every
 * scenario here drives a {@link MutableClock} rather than sleeping real time.
 */
class TokenBucketTest {

    /** Capacity 3 (the burst), refilling at 60 tokens/minute = exactly one token per second. */
    private static final RouteLimit LIMIT = new RouteLimit(3, 60, Duration.ofMinutes(1));

    private final MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));

    private final TokenBucket bucket = new TokenBucket(LIMIT, clock);

    @Test
    @DisplayName("a fresh bucket allows up to its capacity")
    void givenAFreshBucket_whenConsumingUpToCapacity_thenAllSucceed() {
        assertTrue(bucket.consume().allowed());
        assertTrue(bucket.consume().allowed());
        assertTrue(bucket.consume().allowed());
    }

    @Test
    @DisplayName("a bucket exhausted of its capacity rejects the next request with a positive retry-after")
    void givenAnExhaustedBucket_whenConsumingAgain_thenItIsRejectedWithARetryAfter() {
        bucket.consume();
        bucket.consume();
        bucket.consume();

        final var decision = bucket.consume();

        assertFalse(decision.allowed());
        assertTrue(decision.retryAfterSeconds() > 0);
    }

    @Test
    @DisplayName("after enough time passes, a refilled token becomes available")
    void givenTimeHasPassedByTheRefillPeriod_whenConsumingAgain_thenATokenIsAvailable() {
        bucket.consume();
        bucket.consume();
        bucket.consume();
        assertFalse(bucket.consume().allowed());

        clock.advance(Duration.ofSeconds(1));

        assertTrue(bucket.consume().allowed());
    }

    @Test
    @DisplayName("refill never exceeds capacity, however long the bucket sits idle")
    void givenALongIdlePeriod_whenConsuming_thenTokensNeverExceedCapacity() {
        bucket.consume();
        clock.advance(Duration.ofHours(1));

        assertTrue(bucket.consume().allowed());
        assertTrue(bucket.consume().allowed());
        assertTrue(bucket.consume().allowed());
        assertFalse(bucket.consume().allowed());
    }

    @Test
    @DisplayName("consuming exactly at capacity with no elapsed time leaves nothing for a concurrent-looking next call")
    void givenNoElapsedTime_whenConsumingOneMoreThanCapacity_thenTheLastOneIsRejected() {
        for (int i = 0; i < LIMIT.capacity(); i++) {
            assertTrue(bucket.consume().allowed());
        }

        assertFalse(bucket.consume().allowed());
    }
}
