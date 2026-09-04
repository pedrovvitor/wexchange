package com.pedrolima.wexchange.adapter.out.fiscal;

/**
 * The response body exceeded the configured cap and reading it was aborted
 * before it was fully materialized. A policy decision, not a transport
 * failure, so it is never retried.
 */
final class ResponseTooLargeException extends RuntimeException {

    ResponseTooLargeException(final long maxBytes) {
        super("Response body exceeded the configured cap of " + maxBytes + " bytes");
    }
}
