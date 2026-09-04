package com.pedrolima.wexchange.adapter.out.fiscal;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import java.time.Duration;

/**
 * Builds the three resilience4j policies {@link HttpFiscalDataClient} composes
 * around every attempt, kept in one place per issue #3's instruction not to
 * stack independent retry policies between the client and its callers.
 */
final class FiscalResiliencePolicies {

    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;

    FiscalResiliencePolicies(final FiscalClientProperties properties) {
        this.retry = buildRetry(properties);
        this.circuitBreaker = buildCircuitBreaker(properties.circuitBreaker());
        this.bulkhead = buildBulkhead(properties);
    }

    Retry retry() {
        return retry;
    }

    CircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }

    Bulkhead bulkhead() {
        return bulkhead;
    }

    private static Retry buildRetry(final FiscalClientProperties properties) {
        final IntervalFunction defaultBackoff = IntervalFunction.ofExponentialRandomBackoff(
                properties.initialBackoff(), properties.backoffMultiplier(), properties.jitterFactor());

        final RetryConfig config = RetryConfig.<RawResponse>custom()
                .maxAttempts(properties.maxAttempts())
                .intervalBiFunction((attempt, either) -> retryAfterOrDefault(either, defaultBackoff, attempt))
                .retryOnException(FiscalResiliencePolicies::isRetryable)
                .build();
        return Retry.of("fiscalDataClient", config);
    }

    static long retryAfterOrDefault(
            final Either<Throwable, RawResponse> either,
            final IntervalFunction defaultBackoff,
            final int attempt
    ) {
        if (either.isLeft() && either.getLeft() instanceof UpstreamHttpStatusException statusException
                && statusException.retryAfter() != null) {
            return statusException.retryAfter().toMillis();
        }
        return defaultBackoff.apply(attempt);
    }

    static boolean isRetryable(final Throwable throwable) {
        if (throwable instanceof UpstreamIoException) {
            return true;
        }
        return throwable instanceof UpstreamHttpStatusException statusException && statusException.retryable();
    }

    private static CircuitBreaker buildCircuitBreaker(final FiscalClientProperties.CircuitBreakerSettings settings) {
        final CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(settings.failureRateThreshold())
                .slidingWindowSize(settings.slidingWindowSize())
                .waitDurationInOpenState(settings.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(settings.permittedCallsInHalfOpenState())
                .ignoreExceptions(UpstreamInterruptedException.class, BulkheadFullException.class)
                .recordExceptions(UpstreamIoException.class, UpstreamHttpStatusException.class, ResponseTooLargeException.class)
                .build();
        return CircuitBreaker.of("fiscalDataClient", config);
    }

    private static Bulkhead buildBulkhead(final FiscalClientProperties properties) {
        final BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(properties.maxConcurrentCalls())
                .maxWaitDuration(Duration.ZERO)
                .build();
        return Bulkhead.of("fiscalDataClient", config);
    }
}
