package com.pedrolima.wexchange.adapter.out.fiscal;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FiscalClientPropertiesTest {

    @Test
    void givenNoValuesSupplied_whenConstructed_thenSafeDefaultsApply() {
        final var properties = new FiscalClientProperties(
                null, null, null, 0, 0, 0, null, 0, 0, null, 0, null);

        assertEquals(Duration.ofSeconds(2), properties.connectTimeout());
        assertEquals(Duration.ofSeconds(5), properties.requestTimeout());
        assertEquals(Duration.ofSeconds(15), properties.totalDeadline());
        assertEquals(5_242_880L, properties.maxResponseBytes());
        assertEquals(4, properties.maxConcurrentCalls());
        assertEquals(4, properties.maxAttempts());
        assertEquals(Duration.ofMillis(200), properties.initialBackoff());
        assertEquals(2.0, properties.backoffMultiplier());
        assertEquals(0.5, properties.jitterFactor());
        assertEquals(Set.of(429, 502, 503, 504), properties.retryableStatusCodes());
        assertEquals(20, properties.maxPages());
        assertEquals(50f, properties.circuitBreaker().failureRateThreshold());
        assertEquals(20, properties.circuitBreaker().slidingWindowSize());
        assertEquals(Duration.ofSeconds(30), properties.circuitBreaker().waitDurationInOpenState());
        assertEquals(5, properties.circuitBreaker().permittedCallsInHalfOpenState());
    }

    @Test
    void givenExplicitValues_whenConstructed_thenTheyAreKeptVerbatim() {
        final var properties = new FiscalClientProperties(
                Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(9),
                1024, 2, 6, Duration.ofMillis(50), 3.0, 0.1, Set.of(503), 5,
                new FiscalClientProperties.CircuitBreakerSettings(75f, 10, Duration.ofSeconds(20), 3));

        assertEquals(Duration.ofSeconds(1), properties.connectTimeout());
        assertEquals(1024, properties.maxResponseBytes());
        assertEquals(Set.of(503), properties.retryableStatusCodes());
        assertEquals(75f, properties.circuitBreaker().failureRateThreshold());
    }
}
