package com.pedrolima.wexchange.adapter.out.fiscal;

/**
 * The calling thread was interrupted while an attempt was in flight. Never
 * retried - retrying would ignore the interrupt signal - and never counted as
 * a circuit-breaker failure, since it says nothing about the provider's health.
 */
final class UpstreamInterruptedException extends RuntimeException {

    UpstreamInterruptedException(final InterruptedException cause) {
        super("Interrupted while calling the fiscal data API", cause);
    }
}
