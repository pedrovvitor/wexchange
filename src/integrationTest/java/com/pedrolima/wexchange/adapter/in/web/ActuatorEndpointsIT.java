package com.pedrolima.wexchange.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Health, readiness/liveness, and Prometheus exposure (issue #9) through the
 * real filter chain and servlet container - the same reason
 * {@link AbstractPostgresApplicationIT} exists rather than a mocked one.
 *
 * <p>{@code @AutoConfigureMetrics} is required here: {@code @SpringBootTest}
 * disables every metrics-export auto-configuration by default (so a test
 * suite never accidentally publishes to a real backend), which would
 * otherwise make {@code /actuator/prometheus} 404 in this test even though it
 * is genuinely available outside of tests.
 */
@AutoConfigureMetrics
class ActuatorEndpointsIT extends AbstractPostgresApplicationIT {

    @Test
    void givenHealth_whenRequested_thenItReportsUpWithoutLeakingComponentDetails() {
        final ResponseEntity<String> response = restTemplate.getForEntity(baseUrl("/actuator/health"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() != null && response.getBody().contains("\"status\":\"UP\""));
        assertFalse(response.getBody().contains("\"components\""), "show-details: never must keep component internals out of the response");
    }

    @Test
    void givenReadiness_whenRequested_thenItReportsUpBecauseTheDatabaseIsReachable() {
        final ResponseEntity<String> response =
                restTemplate.getForEntity(baseUrl("/actuator/health/readiness"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() != null && response.getBody().contains("\"status\":\"UP\""));
    }

    @Test
    void givenLiveness_whenRequested_thenItReportsUp() {
        final ResponseEntity<String> response =
                restTemplate.getForEntity(baseUrl("/actuator/health/liveness"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() != null && response.getBody().contains("\"status\":\"UP\""));
    }

    @Test
    void givenPrometheus_whenScraped_thenItExposesLowCardinalityApplicationTaggedSeries() {
        final ResponseEntity<String> response = restTemplate.getForEntity(baseUrl("/actuator/prometheus"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        final String body = response.getBody();
        assertTrue(body != null && body.contains("application=\"wexchange\""),
                "expected every series to carry the stable application tag");
    }

    @Test
    void givenPrometheus_whenScraped_thenItIncludesRedAndJdbcPoolSeries() {
        // A prior request guarantees at least one http.server.requests sample
        // exists by the time this test scrapes - the scrape's own request is
        // recorded only after its response is already written, so it cannot
        // prove this on its own regardless of test execution order.
        restTemplate.getForEntity(baseUrl("/actuator/health"), String.class);

        final ResponseEntity<String> response = restTemplate.getForEntity(baseUrl("/actuator/prometheus"), String.class);

        final String body = response.getBody();
        assertTrue(body != null && body.contains("http_server_requests_seconds_count"),
                "expected RED latency/count series for HTTP routes");
        assertTrue(body.contains("hikaricp_connections"), "expected HikariCP pool series to auto-register");
        assertTrue(body.contains("wexchange_application_scheduler_country_currency_sync_last_success_epoch_seconds"),
                "expected the scheduler-freshness gauge");
    }

    @Test
    void givenAnUnexposedActuatorEndpoint_whenRequested_thenItIsNotFound() {
        final ResponseEntity<String> response = restTemplate.getForEntity(baseUrl("/actuator/env"), String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
