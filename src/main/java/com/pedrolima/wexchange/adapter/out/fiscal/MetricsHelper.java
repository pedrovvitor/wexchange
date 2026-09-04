package com.pedrolima.wexchange.adapter.out.fiscal;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class MetricsHelper {

    private final MeterRegistry meterRegistry;

    public void registryUpsertCountryCurrenciesElapsedTime(final long time) {
        Timer.builder("wexchange.application.update..retrieval.time")
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(Duration.ofNanos(time));
    }

    public void incrementUnmappedExceptionMetric() {
        Counter.builder("wexchange.application.unmapped.error.count")
                .register(meterRegistry)
                .increment();
    }
}
