package com.pedrolima.wexchange.util;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class MetricsUtilsTest {

    private MetricsUtils metricsUtils;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsUtils = new MetricsUtils(meterRegistry);
    }

    @Test
    public void registryExchangeRateRetrievalElapsedTime_shouldRecordElapsedTime() {
        long time = 1000;
        metricsUtils.registryExchangeRateRetrievalElapsedTime(time);

        Timer timer = meterRegistry.find("wexchange.application.exchange.rate.retrieval.time").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(time);
    }

    @Test
    public void incrementRequestErrorMetric_shouldIncrementRequestErrorCount() {
        metricsUtils.incrementRequestErrorMetric();

        Counter counter = meterRegistry.find("wexchange.application.integration.fiscal.request.error.count").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    public void incrementParsingErrorMetric_shouldIncrementParsingErrorCount() {
        metricsUtils.incrementParsingErrorMetric();

        Counter counter = meterRegistry.find("wexchange.application.parsing.error.count").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    public void incrementUnmappedExceptionMetric_shouldIncrementUnmappedErrorCount() {
        metricsUtils.incrementUnmappedExceptionMetric();

        Counter counter = meterRegistry.find("wexchange.application.unmapped.error.count").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);
    }
}

