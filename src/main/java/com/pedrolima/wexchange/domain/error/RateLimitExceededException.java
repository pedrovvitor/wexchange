package com.pedrolima.wexchange.domain.error;

/**
 * Thrown when a caller has exhausted its rate-limit budget for a route or
 * globally (issue #17). {@code retryAfterSeconds} is how long the caller must
 * wait before its next token becomes available - carried on the exception so
 * {@code GlobalExceptionHandler} can echo it back as the RFC 9457 response's
 * {@code Retry-After} header.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(final String message, final long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
