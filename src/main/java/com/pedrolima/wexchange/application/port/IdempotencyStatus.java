package com.pedrolima.wexchange.application.port;

/**
 * The lifecycle of one idempotency-key record (issue #18).
 *
 * <p>{@code IN_PROGRESS} is the state a claim starts in; a request that
 * crashes or is abandoned before reaching {@code COMPLETED} leaves its record
 * in {@code FAILED} instead, which is what lets a later request with the same
 * key retry rather than being replayed a failure forever or blocked forever.
 */
public enum IdempotencyStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
