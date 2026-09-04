package com.pedrolima.wexchange.adapter.in.web;

import com.pedrolima.wexchange.bootstrap.Main;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * A real running application (issue #16), for the tests that need to exercise
 * the actual servlet filter chain - CORS, security headers - rather than a
 * mocked or standalone MVC setup that never runs it. Uses the Testcontainers
 * singleton pattern, same as {@code AbstractPostgresRepositoryIT}.
 */
@SpringBootTest(classes = Main.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractPostgresApplicationIT {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // The real Main context also boots the fiscal @Scheduled sync job and,
        // on purchase creation, the @Async rate refresh - both real network
        // calls in production. Nothing on this port is listening, so they fail
        // fast locally instead of this suite reaching the public internet.
        registry.add("fiscal.service.api.endpoint", () -> "http://127.0.0.1:1/");
        // Disables the country-currency scheduled sync bean entirely (issue
        // #6's @ConditionalOnProperty, not just a no-op cron) - relying on its
        // "0 0 2 * * *" cron simply not matching during a short test run would
        // make "the sync never fires in tests" a matter of luck, not a
        // guarantee.
        registry.add("app.country-currency-sync.enabled", () -> "false");
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    protected String baseUrl(final String path) {
        return "http://localhost:" + port + path;
    }
}
