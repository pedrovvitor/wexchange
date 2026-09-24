package com.pedrolima.wexchange.adapter.out.fiscal;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CountryCurrencySyncMetricsTest {

    private static final String PREFIX = "wexchange.application.scheduler.country_currency_sync.";

    @Test
    @DisplayName("recording success sets the last-success gauge to that instant's epoch second")
    void givenAnInstant_whenRecordingSuccess_thenTheLastSuccessGaugeReflectsIt() {
        final var meterRegistry = new SimpleMeterRegistry();
        final var metrics = new CountryCurrencySyncMetrics(meterRegistry);
        final var at = Instant.parse("2024-07-15T12:00:00Z");

        metrics.recordSuccess(at);

        assertEquals(
                at.getEpochSecond(),
                meterRegistry.get(PREFIX + "last.success.epoch.seconds").gauge().value());
    }

    @Test
    @DisplayName("recording failure sets the last-failure gauge to that instant's epoch second")
    void givenAnInstant_whenRecordingFailure_thenTheLastFailureGaugeReflectsIt() {
        final var meterRegistry = new SimpleMeterRegistry();
        final var metrics = new CountryCurrencySyncMetrics(meterRegistry);
        final var at = Instant.parse("2024-07-15T12:00:00Z");

        metrics.recordFailure(at);

        assertEquals(
                at.getEpochSecond(),
                meterRegistry.get(PREFIX + "last.failure.epoch.seconds").gauge().value());
    }

    @Test
    @DisplayName("recording a duration adds one sample to the duration timer with that elapsed time")
    void givenADuration_whenRecordingDuration_thenTheTimerCapturesIt() {
        final var meterRegistry = new SimpleMeterRegistry();
        final var metrics = new CountryCurrencySyncMetrics(meterRegistry);

        metrics.recordDuration(Duration.ofMillis(250));

        final var timer = meterRegistry.get(PREFIX + "duration").timer();
        assertEquals(1, timer.count());
        assertEquals(250.0, timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));
    }
}
