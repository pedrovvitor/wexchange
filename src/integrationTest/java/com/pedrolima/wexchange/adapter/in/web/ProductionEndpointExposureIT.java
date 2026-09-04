package com.pedrolima.wexchange.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Production must not expose interactive API documentation (issue #16 / ADR
 * 0002): a public deployment convenience becomes an unnecessary disclosure
 * surface once it is live. {@link SecurityHeadersIT} and
 * {@link CorsConfigurationIT} already prove the default (no-profile) boot
 * still serves it - a development convenience that stays intentional there.
 */
@ActiveProfiles("production")
class ProductionEndpointExposureIT extends AbstractPostgresApplicationIT {

    @Test
    void givenTheProductionProfile_whenRequestingTheApiDocs_thenTheyAreNotServed() {
        final ResponseEntity<String> response = restTemplate.getForEntity(baseUrl("/v3/api-docs"), String.class);

        assertTrue(response.getStatusCode().is4xxClientError(),
                "expected the API docs to be unreachable in production, got " + response.getStatusCode());
    }

    @Test
    void givenTheProductionProfile_whenRequestingSwaggerUi_thenItIsNotServed() {
        final ResponseEntity<String> response = restTemplate.getForEntity(baseUrl("/swagger-ui/index.html"), String.class);

        assertTrue(response.getStatusCode().is4xxClientError(),
                "expected Swagger UI to be unreachable in production, got " + response.getStatusCode());
    }
}
