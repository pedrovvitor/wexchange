package com.pedrolima.wexchange.adapter.out.fiscal;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Last-success, last-failure, and duration for the country-currency
 * scheduled sync (issue #6/#9). Success and failure are gauges rather than
 * counters: what an operator needs to answer "is this job still healthy" is
 * *when* it last worked or broke, not how many times it has run in total.
 *
 * <p>{@code recordDuration} takes a {@link Duration} rather than a raw
 * {@code long}, deliberately: {@code MetricsHelper}, this class's
 * predecessor, took an unlabelled {@code long} nanosecond count, which is
 * exactly the "implicit numeric unit" shape issue #9 identifies as a risk -
 * nothing about the parameter itself told a caller or a reviewer which unit
 * was expected. It also carried a malformed metric name
 * ({@code wexchange.application.update..retrieval.time}, note the double
 * dot) and a second method, {@code incrementUnmappedExceptionMetric}, that
 * was never called from any production code path - dead instrumentation.
 * Neither problem is inherited here: the duration metric moves to this
 * already-existing, correctly-scoped class, and the unmapped-exception
 * counter moves to {@code GlobalExceptionMetrics} in the web layer, which is
 * where it is actually usable ({@code MetricsHelper} lived in
 * {@code adapter.out.fiscal}, a layer {@code adapter.in.web} is not permitted
 * to depend on).
 */
@Component
public class CountryCurrencySyncMetrics {

    private static final String PREFIX = "wexchange.application.scheduler.country_currency_sync.";

    private final AtomicLong lastSuccessEpochSeconds = new AtomicLong();
    private final AtomicLong lastFailureEpochSeconds = new AtomicLong();
    private final Timer duration;

    public CountryCurrencySyncMetrics(final MeterRegistry meterRegistry) {
        Gauge.builder(PREFIX + "last.success.epoch.seconds", lastSuccessEpochSeconds, AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder(PREFIX + "last.failure.epoch.seconds", lastFailureEpochSeconds, AtomicLong::get)
                .register(meterRegistry);
        this.duration = Timer.builder(PREFIX + "duration")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    void recordSuccess(final Instant at) {
        lastSuccessEpochSeconds.set(at.getEpochSecond());
    }

    void recordFailure(final Instant at) {
        lastFailureEpochSeconds.set(at.getEpochSecond());
    }

    void recordDuration(final Duration elapsed) {
        duration.record(elapsed);
    }
}
