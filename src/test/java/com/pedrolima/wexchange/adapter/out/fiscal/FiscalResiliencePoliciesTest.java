package com.pedrolima.wexchange.adapter.out.fiscal;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.core.functions.Either;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiscalResiliencePoliciesTest {

    @Test
    void givenAnUpstreamIoException_whenCheckingRetryability_thenItIsRetryable() {
        assertTrue(FiscalResiliencePolicies.isRetryable(new UpstreamIoException(new IOException("boom"))));
    }

    @Test
    void givenARetryableStatusException_whenCheckingRetryability_thenItIsRetryable() {
        assertTrue(FiscalResiliencePolicies.isRetryable(new UpstreamHttpStatusException(503, true, null)));
    }

    @Test
    void givenANonRetryableStatusException_whenCheckingRetryability_thenItIsNotRetryable() {
        assertFalse(FiscalResiliencePolicies.isRetryable(new UpstreamHttpStatusException(400, false, null)));
    }

    @Test
    void givenAnUnrelatedException_whenCheckingRetryability_thenItIsNotRetryable() {
        assertFalse(FiscalResiliencePolicies.isRetryable(new IllegalStateException("unexpected")));
    }

    @Test
    void givenAStatusExceptionWithARetryAfter_whenComputingTheNextInterval_thenItOverridesTheDefaultBackoff() {
        final Either<Throwable, RawResponse> failure =
                Either.left(new UpstreamHttpStatusException(429, true, Duration.ofSeconds(7)));
        final IntervalFunction defaultBackoff = IntervalFunction.of(Duration.ofMillis(50));

        assertEquals(7000L, FiscalResiliencePolicies.retryAfterOrDefault(failure, defaultBackoff, 1));
    }

    @Test
    void givenAFailureWithNoRetryAfter_whenComputingTheNextInterval_thenTheDefaultBackoffIsUsed() {
        final Either<Throwable, RawResponse> failure = Either.left(new UpstreamIoException(new IOException("boom")));
        final IntervalFunction defaultBackoff = IntervalFunction.of(Duration.ofMillis(50));

        assertEquals(50L, FiscalResiliencePolicies.retryAfterOrDefault(failure, defaultBackoff, 1));
    }
}
