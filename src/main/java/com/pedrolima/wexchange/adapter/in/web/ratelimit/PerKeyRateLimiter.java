package com.pedrolima.wexchange.adapter.in.web.ratelimit;

import com.pedrolima.wexchange.adapter.in.web.ratelimit.AbuseControlProperties.RouteLimit;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-key (caller + route) rate limiting (issue #17): each distinct key owns
 * its own {@link TokenBucket}, created lazily on first use with whatever
 * {@link RouteLimit} that key is first seen under. Keys never expire - an
 * anonymous caller's identity here is its remote address, and the set of
 * distinct addresses seen is bounded by actual traffic, not by an attacker's
 * choice, so unbounded growth is not a realistic abuse vector on its own.
 */
@Component
public class PerKeyRateLimiter {

    private final ConcurrentMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;

    public PerKeyRateLimiter(final Clock clock) {
        this.clock = clock;
    }

    public RateLimitDecision consume(final String key, final RouteLimit limit) {
        return buckets.computeIfAbsent(key, k -> new TokenBucket(limit, clock)).consume();
    }
}
