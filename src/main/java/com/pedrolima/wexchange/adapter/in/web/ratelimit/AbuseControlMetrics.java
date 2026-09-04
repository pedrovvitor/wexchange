package com.pedrolima.wexchange.adapter.in.web.ratelimit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Rejection counts for anonymous-abuse controls (issue #17). Tags are the
 * route name and rejection reason only - both fixed, low-cardinality enums,
 * never the caller's address or any request-supplied value, per this
 * project's metrics-labelling rule.
 */
@Component
@RequiredArgsConstructor
public class AbuseControlMetrics {

    private static final String PREFIX = "wexchange.application.web.abuse.";

    private final MeterRegistry meterRegistry;

    /** {@code scope} is either a {@link Route} name or the literal {@code "global"}. */
    void incrementRateLimitRejection(final String scope) {
        Counter.builder(PREFIX + "rejected.count")
                .tag("reason", "rate-limit")
                .tag("route", scope)
                .register(meterRegistry)
                .increment();
    }

    void incrementPayloadTooLargeRejection(final Route route) {
        Counter.builder(PREFIX + "rejected.count")
                .tag("reason", "payload-too-large")
                .tag("route", route.name())
                .register(meterRegistry)
                .increment();
    }

    void incrementInvalidQueryRejection(final Route route) {
        Counter.builder(PREFIX + "rejected.count")
                .tag("reason", "invalid-query")
                .tag("route", route.name())
                .register(meterRegistry)
                .increment();
    }
}
