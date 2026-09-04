package com.pedrolima.wexchange.adapter.in.web.ratelimit;

import com.pedrolima.wexchange.adapter.in.web.ratelimit.AbuseControlProperties.RouteLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Coalescing per key (issue #17): one caller exhausting its budget must never affect another's. */
class PerKeyRateLimiterTest {

    private static final RouteLimit LIMIT = new RouteLimit(1, 10, Duration.ofMinutes(1));

    private final MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));

    private final PerKeyRateLimiter limiter = new PerKeyRateLimiter(clock);

    @Test
    @DisplayName("a key's own bucket is created lazily and starts full")
    void givenANewKey_whenConsuming_thenItStartsWithAFullBucket() {
        assertTrue(limiter.consume("1.2.3.4:route", LIMIT).allowed());
    }

    @Test
    @DisplayName("exhausting one key's budget does not affect a different key")
    void givenOneKeyIsExhausted_whenAnotherKeyConsumes_thenItIsUnaffected() {
        assertTrue(limiter.consume("1.2.3.4:route", LIMIT).allowed());
        assertFalse(limiter.consume("1.2.3.4:route", LIMIT).allowed());

        assertTrue(limiter.consume("5.6.7.8:route", LIMIT).allowed());
    }

    @Test
    @DisplayName("the same key is rejected once its own capacity is spent")
    void givenTheSameKey_whenConsumingBeyondCapacity_thenItIsRejected() {
        assertTrue(limiter.consume("1.2.3.4:route", LIMIT).allowed());

        assertFalse(limiter.consume("1.2.3.4:route", LIMIT).allowed());
    }
}
