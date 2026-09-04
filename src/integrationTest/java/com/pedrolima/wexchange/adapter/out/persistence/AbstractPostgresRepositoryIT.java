package com.pedrolima.wexchange.adapter.out.persistence;

import com.pedrolima.wexchange.bootstrap.Main;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One real PostgreSQL container, shared by every repository integration test
 * (issue #5) via the Testcontainers singleton pattern: started once in a
 * static initializer, never stopped explicitly, and torn down by
 * Testcontainers' own Ryuk reaper when the JVM exits. Starting a fresh
 * container per test class would work too, but costs several seconds each
 * time for no isolation benefit - {@code @DataJpaTest}'s per-test transaction
 * rollback already isolates test cases from each other.
 *
 * <p>{@code Main} lives outside this package's ancestry, so
 * {@code @DataJpaTest}'s default configuration search - which only walks
 * upward - would never find it without this explicit override.
 */
@DataJpaTest
@ContextConfiguration(classes = Main.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
abstract class AbstractPostgresRepositoryIT {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
