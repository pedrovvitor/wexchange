package com.pedrolima.wexchange.adapter.out.fiscal;

/**
 * A transport-level failure (connection refused, reset, malformed response
 * line) on one attempt against the fiscal provider. Always retryable: it says
 * nothing about whether the request itself was valid.
 */
final class UpstreamIoException extends RuntimeException {

    UpstreamIoException(final Throwable cause) {
        super(cause.getMessage(), cause);
    }
}
