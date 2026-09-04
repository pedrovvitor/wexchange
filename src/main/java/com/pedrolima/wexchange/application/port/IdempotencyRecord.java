package com.pedrolima.wexchange.application.port;

import java.time.Instant;

/**
 * A stored idempotency-key claim (issue #18): what request it fingerprints,
 * how far it got, and - once {@link IdempotencyStatus#COMPLETED} - which
 * resource it produced, so a replay can look that resource up instead of
 * re-running the request.
 */
public record IdempotencyRecord(
        String idempotencyKey,
        String fingerprint,
        IdempotencyStatus status,
        String resourceId,
        Instant expiresAt
) {
}
