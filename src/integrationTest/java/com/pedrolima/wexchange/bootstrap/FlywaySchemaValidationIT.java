package com.pedrolima.wexchange.bootstrap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves the acceptance criterion in issue #5 that a schema mismatch fails
 * startup rather than being silently tolerated: with migrations disabled, the
 * database stays empty, and {@code spring.jpa.hibernate.ddl-auto=validate}
 * must refuse to start the application against it.
 *
 * <p>Deliberately its own full {@link SpringApplicationBuilder} run rather
 * than a {@code @SpringBootTest} slice: this is exercising application
 * startup itself, not one collaborating component.
 */
class FlywaySchemaValidationIT {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    @Test
    void givenMigrationsAreDisabledSoTheSchemaNeverExists_whenStartingTheApplication_thenStartupFailsFast() {
        final var builder = new SpringApplicationBuilder(Main.class);

        // Command-line-style args, not .properties(...): the latter registers
        // *default* properties, ranked below application.yml's own
        // ${DATABASE_POSTGRES_URL} - too low to actually override it.
        assertThrows(BeanCreationException.class, () -> builder.run(
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.flyway.enabled=false",
                "--server.port=0"));
    }
}
