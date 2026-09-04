package com.pedrolima.wexchange.adapter.in.web.ratelimit;

/** Whether a request may proceed, and if not, how long until it may retry. */
public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
}
