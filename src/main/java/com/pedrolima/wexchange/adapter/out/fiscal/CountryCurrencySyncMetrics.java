package com.pedrolima.wexchange.adapter.out.fiscal;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Last-success and last-failure timestamps for the country-currency
 * scheduled sync (issue #6), as gauges rather than counters: what an operator
 * needs to answer "is this job still healthy" is *when* it last worked or
 * broke, not how many times it has run in total.
 */
@Component
public class CountryCurrencySyncMetrics {

    private static final String PREFIX = "wexchange.application.scheduler.country_currency_sync.";

    private final AtomicLong lastSuccessEpochSeconds = new AtomicLong();
    private final AtomicLong lastFailureEpochSeconds = new AtomicLong();

    public CountryCurrencySyncMetrics(final MeterRegistry meterRegistry) {
        Gauge.builder(PREFIX + "last.success.epoch.seconds", lastSuccessEpochSeconds, AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder(PREFIX + "last.failure.epoch.seconds", lastFailureEpochSeconds, AtomicLong::get)
                .register(meterRegistry);
    }

    void recordSuccess(final Instant at) {
        lastSuccessEpochSeconds.set(at.getEpochSecond());
    }

    void recordFailure(final Instant at) {
        lastFailureEpochSeconds.set(at.getEpochSecond());
    }
}
