package com.pedrolima.wexchange.adapter.out.fiscal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Set;

/**
 * Every bound-latency, retry, circuit-breaker, and safety knob for the fiscal
 * HTTP client, externalized so operators can tune them per environment without
 * a code change. Defaults are conservative enough to be safe as shipped.
 */
@ConfigurationProperties(prefix = "fiscal.client")
public record FiscalClientProperties(
        Duration connectTimeout,
        Duration requestTimeout,
        Duration totalDeadline,
        long maxResponseBytes,
        int maxConcurrentCalls,
        int maxAttempts,
        Duration initialBackoff,
        double backoffMultiplier,
        double jitterFactor,
        Set<Integer> retryableStatusCodes,
        int maxPages,
        CircuitBreakerSettings circuitBreaker
) {

    public FiscalClientProperties {
        connectTimeout = defaultIfNull(connectTimeout, Duration.ofSeconds(2));
        requestTimeout = defaultIfNull(requestTimeout, Duration.ofSeconds(5));
        totalDeadline = defaultIfNull(totalDeadline, Duration.ofSeconds(15));
        maxResponseBytes = maxResponseBytes > 0 ? maxResponseBytes : 5_242_880L;
        maxConcurrentCalls = maxConcurrentCalls > 0 ? maxConcurrentCalls : 4;
        maxAttempts = maxAttempts > 0 ? maxAttempts : 4;
        initialBackoff = defaultIfNull(initialBackoff, Duration.ofMillis(200));
        backoffMultiplier = backoffMultiplier > 0 ? backoffMultiplier : 2.0;
        jitterFactor = jitterFactor > 0 ? jitterFactor : 0.5;
        retryableStatusCodes = retryableStatusCodes != null && !retryableStatusCodes.isEmpty()
                ? Set.copyOf(retryableStatusCodes)
                : Set.of(429, 502, 503, 504);
        maxPages = maxPages > 0 ? maxPages : 20;
        circuitBreaker = circuitBreaker != null ? circuitBreaker : CircuitBreakerSettings.defaults();
    }

    private static Duration defaultIfNull(final Duration value, final Duration fallback) {
        return value != null ? value : fallback;
    }

    public record CircuitBreakerSettings(
            float failureRateThreshold,
            int slidingWindowSize,
            Duration waitDurationInOpenState,
            int permittedCallsInHalfOpenState
    ) {

        public CircuitBreakerSettings {
            failureRateThreshold = failureRateThreshold > 0 ? failureRateThreshold : 50f;
            slidingWindowSize = slidingWindowSize > 0 ? slidingWindowSize : 20;
            waitDurationInOpenState = waitDurationInOpenState != null ? waitDurationInOpenState : Duration.ofSeconds(30);
            permittedCallsInHalfOpenState = permittedCallsInHalfOpenState > 0 ? permittedCallsInHalfOpenState : 5;
        }

        public static CircuitBreakerSettings defaults() {
            return new CircuitBreakerSettings(0, 0, null, 0);
        }
    }
}
