package com.pedrolima.wexchange.adapter.in.web;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * How often {@link GlobalExceptionHandler}'s sanitized-500 fallback fires
 * (issue #9) - every other handler in that class maps to a specific,
 * expected failure; reaching this one means something genuinely
 * unclassified happened, which is worth alerting on independently of any
 * single exception's message (never included as a label - see the class
 * Javadoc on why the message itself is never surfaced to a caller).
 */
@Component
@RequiredArgsConstructor
public class GlobalExceptionMetrics {

    private static final String UNMAPPED_ERROR_COUNT = "wexchange.application.web.unmapped_error.count";

    private final MeterRegistry meterRegistry;

    void incrementUnmappedError() {
        Counter.builder(UNMAPPED_ERROR_COUNT).register(meterRegistry).increment();
    }
}
