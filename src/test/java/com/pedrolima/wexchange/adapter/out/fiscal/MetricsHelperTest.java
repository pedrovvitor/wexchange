package com.pedrolima.wexchange.adapter.out.fiscal;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class MetricsHelperTest {

    private MetricsHelper metricsHelper;

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsHelper = new MetricsHelper(meterRegistry);
    }

    @Test
    public void incrementUnmappedExceptionMetric_shouldIncrementUnmappedErrorCount() {
        metricsHelper.incrementUnmappedExceptionMetric();

        Counter counter = meterRegistry.find("wexchange.application.unmapped.error.count").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    public void registryUpsertCountryCurrenciesElapsedTime_shouldRecordElapsedTime() {
        long time = 2500;
        metricsHelper.registryUpsertCountryCurrenciesElapsedTime(time);

        Timer timer = meterRegistry.find("wexchange.application.update..retrieval.time").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(time);
    }
}
