package com.pedrolima.wexchange.adapter.out.persistence;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** How many anonymous purchases the retention cleanup job (issue #17) has deleted. */
@Component
@RequiredArgsConstructor
public class PurchaseRetentionMetrics {

    private static final String PREFIX = "wexchange.application.purchase.retention.";

    private final MeterRegistry meterRegistry;

    void recordDeleted(final long count) {
        Counter.builder(PREFIX + "deleted.count").register(meterRegistry).increment(count);
    }
}
