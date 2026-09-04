package com.pedrolima.wexchange.adapter.out.fiscal;

import java.time.Duration;

/**
 * The provider answered, but not with a usable 2xx: a deterministic client
 * error, an untrusted or excessive redirect, or a server-side failure.
 * {@code retryable} tells the retry policy whether attempting again could ever
 * help; {@code retryAfter}, when the provider sent one, is honoured verbatim
 * instead of the default backoff.
 */
final class UpstreamHttpStatusException extends RuntimeException {

    private final int statusCode;
    private final boolean retryable;
    private final Duration retryAfter;

    UpstreamHttpStatusException(final int statusCode, final boolean retryable, final Duration retryAfter) {
        super("Unexpected response status from fiscal data API: " + statusCode);
        this.statusCode = statusCode;
        this.retryable = retryable;
        this.retryAfter = retryAfter;
    }

    int statusCode() {
        return statusCode;
    }

    boolean retryable() {
        return retryable;
    }

    Duration retryAfter() {
        return retryAfter;
    }
}
