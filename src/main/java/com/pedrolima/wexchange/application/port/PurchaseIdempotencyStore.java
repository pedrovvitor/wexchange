package com.pedrolima.wexchange.application.port;

import java.time.Instant;
import java.util.Optional;

/**
 * Storage for purchase-creation idempotency claims (issue #18). Implemented
 * by the persistence adapter, whose database unique constraint on the key is
 * the final concurrency defense beneath every method here: {@link #claim} can
 * race with another replica's identical call, and the constraint - not this
 * interface - is what guarantees only one of them ever succeeds.
 */
public interface PurchaseIdempotencyStore {

    /**
     * Atomically creates a new {@link IdempotencyStatus#IN_PROGRESS} record for
     * a key nothing is currently claiming. Returns {@code true} if this caller
     * won the claim, {@code false} if a record for this key already exists -
     * the caller then reads it via {@link #find} to decide what to do next.
     */
    boolean claim(String idempotencyKey, String fingerprint, Instant now, Instant expiresAt);

    /**
     * Atomically resets an existing record back to {@link IdempotencyStatus#IN_PROGRESS},
     * but only if it is currently {@link IdempotencyStatus#FAILED} or past its
     * {@code expiresAt}. Returns {@code true} if this caller won the reclaim -
     * concurrent reclaimers of the same stale record must see at most one
     * {@code true}.
     */
    boolean reclaim(String idempotencyKey, Instant now);

    /** Marks a claimed record completed, recording the resource it produced. */
    void markCompleted(String idempotencyKey, String resourceId, Instant now);

    /** Marks a claimed record failed, so a later request may {@link #reclaim} it. */
    void markFailed(String idempotencyKey, Instant now);

    Optional<IdempotencyRecord> find(String idempotencyKey);
}
