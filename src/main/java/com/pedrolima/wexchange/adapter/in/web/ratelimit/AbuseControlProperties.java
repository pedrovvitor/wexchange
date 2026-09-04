package com.pedrolima.wexchange.adapter.in.web.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Bounds on anonymous resource consumption (issue #17): per-route and global
 * rate limits, the maximum request-body size, and the maximum page size a
 * caller of {@code GET /v1/country_currencies} may request. Defaults match
 * the baseline the issue proposes, but every value is deployment-configurable
 * - none of them are meant to be read as a universal production truth.
 */
@ConfigurationProperties(prefix = "app.abuse-control")
public record AbuseControlProperties(
        boolean rateLimitEnabled,
        RouteLimit global,
        RouteLimit purchaseCreation,
        RouteLimit conversion,
        RouteLimit countryCurrencies,
        long maxRequestBodyBytes,
        int maxPageSize,
        int maxCountryCurrencyFilterLength
) {

    public AbuseControlProperties {
        global = defaultIfNull(global, new RouteLimit(20, 100, Duration.ofMinutes(1)));
        purchaseCreation = defaultIfNull(purchaseCreation, new RouteLimit(3, 10, Duration.ofMinutes(1)));
        conversion = defaultIfNull(conversion, new RouteLimit(5, 30, Duration.ofMinutes(1)));
        countryCurrencies = defaultIfNull(countryCurrencies, new RouteLimit(10, 60, Duration.ofMinutes(1)));
        maxRequestBodyBytes = maxRequestBodyBytes > 0 ? maxRequestBodyBytes : 16_384L;
        maxPageSize = maxPageSize > 0 ? maxPageSize : 100;
        maxCountryCurrencyFilterLength = maxCountryCurrencyFilterLength > 0 ? maxCountryCurrencyFilterLength : 100;
    }

    private static RouteLimit defaultIfNull(final RouteLimit value, final RouteLimit fallback) {
        return value != null ? value : fallback;
    }

    /**
     * A token bucket's shape: at most {@code capacity} requests may be spent
     * at once (the burst), refilling by {@code refillTokens} every
     * {@code refillPeriod} thereafter.
     */
    public record RouteLimit(int capacity, int refillTokens, Duration refillPeriod) {

        public RouteLimit {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive");
            }
            if (refillTokens <= 0) {
                throw new IllegalArgumentException("refillTokens must be positive");
            }
            if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
                throw new IllegalArgumentException("refillPeriod must be positive");
            }
        }
    }
}
