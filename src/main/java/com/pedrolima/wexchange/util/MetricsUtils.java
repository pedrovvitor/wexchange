package com.pedrolima.wexchange.util;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class MetricsUtils {

    private final MeterRegistry meterRegistry;

    public void registryExchangeRateRetrievalElapsedTime(final long time) {
        Timer.builder("wexchange.application.exchange.rate.retrieval.time")
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(Duration.ofNanos(time));
    }

    public void incrementRequestErrorMetric() {
        Counter.builder("wexchange.application.integration.fiscal.request.error.count")
                .register(meterRegistry)
                .increment();
    }

    public void incrementParsingErrorMetric() {
        Counter.builder("wexchange.application.parsing.error.count")
                .register(meterRegistry)
                .increment();
    }

    public void incrementUnmappedExceptionMetric() {
        Counter.builder("wexchange.application.unmapped.error.count")
                .register(meterRegistry)
                .increment();
    }
}
