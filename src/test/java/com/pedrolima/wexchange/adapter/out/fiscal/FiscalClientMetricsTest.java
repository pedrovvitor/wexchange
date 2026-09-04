package com.pedrolima.wexchange.adapter.out.fiscal;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies every metric {@link FiscalClientMetrics#bindTo} wires up is
 * actually recorded, by driving real resilience4j events rather than mocking
 * the {@code MeterRegistry}.
 */
class FiscalClientMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private FiscalClientMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new FiscalClientMetrics(meterRegistry);
    }

    @Test
    void givenARetryEvent_whenBound_thenTheAttemptCounterIncrements() {
        final Retry retry = Retry.of("test", io.github.resilience4j.retry.RetryConfig.custom().maxAttempts(3).build());
        metrics.bindTo(retry, CircuitBreaker.ofDefaults("test"), Bulkhead.ofDefaults("test"));

        final AtomicInteger attempts = new AtomicInteger();
        Retry.decorateSupplier(retry, () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new RuntimeException("transient");
            }
            return "ok";
        }).get();

        final Counter counter = meterRegistry.find("wexchange.application.integration.fiscal.retry.attempt.count").counter();
        assertNotNull(counter);
        assertTrue(counter.count() > 0);
    }

    @Test
    void givenACallRejectedByAnOpenCircuit_whenBound_thenTheRejectedCounterIncrements() {
        final CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test");
        circuitBreaker.transitionToOpenState();
        metrics.bindTo(Retry.ofDefaults("test"), circuitBreaker, Bulkhead.ofDefaults("test"));

        assertThrows(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class,
                () -> CircuitBreaker.decorateSupplier(circuitBreaker, () -> "x").get());

        final Counter counter = meterRegistry.find("wexchange.application.integration.fiscal.circuit.rejected.count").counter();
        assertNotNull(counter);
        assertEquals(1, counter.count());
    }

    @Test
    void givenACircuitStateTransition_whenBound_thenTheTransitionCounterIncrements() {
        final CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test");
        metrics.bindTo(Retry.ofDefaults("test"), circuitBreaker, Bulkhead.ofDefaults("test"));

        circuitBreaker.transitionToOpenState();

        final Counter counter = meterRegistry.find("wexchange.application.integration.fiscal.circuit.state.transition.count")
                .tag("to", "OPEN")
                .counter();
        assertNotNull(counter);
        assertEquals(1, counter.count());
    }

    @Test
    void givenASuccessfulCircuitProtectedCall_whenBound_thenCallDurationIsRecordedAsSuccess() {
        final CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test");
        metrics.bindTo(Retry.ofDefaults("test"), circuitBreaker, Bulkhead.ofDefaults("test"));

        CircuitBreaker.decorateSupplier(circuitBreaker, () -> "ok").get();

        final Timer timer = meterRegistry.find("wexchange.application.integration.fiscal.call.duration")
                .tag("outcome", "success")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void givenAFailedCircuitProtectedCall_whenBound_thenCallDurationIsRecordedAsError() {
        final CircuitBreaker circuitBreaker = CircuitBreaker.of("test",
                CircuitBreakerConfig.custom().recordExceptions(RuntimeException.class).build());
        metrics.bindTo(Retry.ofDefaults("test"), circuitBreaker, Bulkhead.ofDefaults("test"));

        assertThrows(RuntimeException.class, () -> CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            throw new RuntimeException("boom");
        }).get());

        final Timer timer = meterRegistry.find("wexchange.application.integration.fiscal.call.duration")
                .tag("outcome", "error")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void givenABulkheadRejection_whenBound_thenTheRejectedCounterIncrements() {
        final Bulkhead bulkhead = Bulkhead.of("test",
                BulkheadConfig.custom().maxConcurrentCalls(1).maxWaitDuration(Duration.ZERO).build());
        metrics.bindTo(Retry.ofDefaults("test"), CircuitBreaker.ofDefaults("test"), bulkhead);

        bulkhead.acquirePermission();
        try {
            assertThrows(BulkheadFullException.class, () -> Bulkhead.decorateSupplier(bulkhead, () -> "x").get());
        } finally {
            bulkhead.releasePermission();
        }

        final Counter counter = meterRegistry.find("wexchange.application.integration.fiscal.bulkhead.rejected.count").counter();
        assertNotNull(counter);
        assertEquals(1, counter.count());
    }

    @Test
    void whenIncrementingSchemaRejectionTwice_thenTheCounterReflectsBoth() {
        metrics.incrementSchemaRejection();
        metrics.incrementSchemaRejection();

        final Counter counter = meterRegistry.find("wexchange.application.integration.fiscal.schema.rejected.count").counter();
        assertNotNull(counter);
        assertEquals(2, counter.count());
    }
}
