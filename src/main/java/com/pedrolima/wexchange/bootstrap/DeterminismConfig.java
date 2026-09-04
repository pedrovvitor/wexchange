package com.pedrolima.wexchange.bootstrap;

import com.pedrolima.wexchange.application.port.IdentifierGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;

/**
 * Binds the two ambient capabilities the application refuses to reach for
 * directly: the current instant and identifier generation.
 *
 * <p>The clock is fixed to UTC. Timestamps are stored and compared without a
 * zone, so letting the host's default zone leak in would make a record's
 * {@code created_at} depend on where the process happens to run.
 */
@Configuration
public class DeterminismConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public IdentifierGenerator identifierGenerator() {
        return () -> UUID.randomUUID().toString();
    }
}
