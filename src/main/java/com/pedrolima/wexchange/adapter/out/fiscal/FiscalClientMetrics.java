package com.pedrolima.wexchange.adapter.out.fiscal;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Attempts, duration, retry activity, circuit-breaker state, bulkhead
 * rejections, and schema rejections for {@link HttpFiscalDataClient} - wired
 * from resilience4j's own event publishers rather than re-derived, so a metric
 * can never drift from what the policy actually did.
 */
@Component
@RequiredArgsConstructor
public class FiscalClientMetrics {

    private static final String PREFIX = "wexchange.application.integration.fiscal.";

    private final MeterRegistry meterRegistry;

    void bindTo(final Retry retry, final CircuitBreaker circuitBreaker, final Bulkhead bulkhead) {
        retry.getEventPublisher().onRetry(event ->
                Counter.builder(PREFIX + "retry.attempt.count")
                        .tag("attempt", String.valueOf(event.getNumberOfRetryAttempts()))
                        .register(meterRegistry)
                        .increment());

        circuitBreaker.getEventPublisher().onCallNotPermitted(event ->
                Counter.builder(PREFIX + "circuit.rejected.count").register(meterRegistry).increment());
        circuitBreaker.getEventPublisher().onStateTransition(event ->
                Counter.builder(PREFIX + "circuit.state.transition.count")
                        .tag("to", event.getStateTransition().getToState().name())
                        .register(meterRegistry)
                        .increment());
        circuitBreaker.getEventPublisher().onSuccess(event ->
                recordCallDuration(event.getElapsedDuration(), "success"));
        circuitBreaker.getEventPublisher().onError(event ->
                recordCallDuration(event.getElapsedDuration(), "error"));

        bulkhead.getEventPublisher().onCallRejected(event ->
                Counter.builder(PREFIX + "bulkhead.rejected.count").register(meterRegistry).increment());
    }

    private void recordCallDuration(final Duration elapsed, final String outcome) {
        Timer.builder(PREFIX + "call.duration")
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(elapsed.toNanos(), TimeUnit.NANOSECONDS);
    }

    void incrementSchemaRejection() {
        Counter.builder(PREFIX + "schema.rejected.count").register(meterRegistry).increment();
    }
}
