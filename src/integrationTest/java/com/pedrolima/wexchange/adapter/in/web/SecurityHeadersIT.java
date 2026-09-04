package com.pedrolima.wexchange.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The security-header baseline (issue #16 / ADR 0002) on a real response,
 * success and error alike - the headers come from a servlet filter, so they
 * apply regardless of which controller or exception handler produced the body.
 */
class SecurityHeadersIT extends AbstractPostgresApplicationIT {

    @Test
    void givenASuccessfulRequest_whenInspectingTheResponse_thenTheSecurityHeaderBaselineIsPresent() {
        final ResponseEntity<String> response = restTemplate.getForEntity(baseUrl("/actuator/health"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertHeaderBaseline(response);
    }

    @Test
    void givenAnErrorResponse_whenInspectingIt_thenTheSecurityHeaderBaselineIsStillPresent() {
        final ResponseEntity<String> response = restTemplate.getForEntity(baseUrl("/v1/purchases/not-a-uuid"), String.class);

        assertTrue(response.getStatusCode().isError(), "expected an error status for a malformed purchase id");
        assertHeaderBaseline(response);
    }

    @Test
    void givenAPlainHttpResponse_whenInspectingIt_thenNoHstsHeaderIsSent() {
        final ResponseEntity<String> response = restTemplate.getForEntity(baseUrl("/actuator/health"), String.class);

        assertNull(response.getHeaders().getFirst("Strict-Transport-Security"),
                "HSTS must stay silent until a verified HTTPS deployment terminates TLS in front of this app");
    }

    @Test
    void givenNoProfileIsActive_whenRequestingSwaggerUi_thenTheDevelopmentConvenienceIsStillServed() {
        final ResponseEntity<String> response = restTemplate.getForEntity(baseUrl("/swagger-ui/index.html"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Swagger UI is a production-only restriction (see ProductionEndpointExposureIT), "
                        + "not a general one");
    }

    private static void assertHeaderBaseline(final ResponseEntity<String> response) {
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals("DENY", response.getHeaders().getFirst("X-Frame-Options"));
        assertEquals("strict-origin-when-cross-origin", response.getHeaders().getFirst("Referrer-Policy"));
        final String permissionsPolicy = response.getHeaders().getFirst("Permissions-Policy");
        assertTrue(permissionsPolicy != null && permissionsPolicy.contains("camera=()"));
        assertFalse(response.getHeaders().containsKey("X-Powered-By"));
    }
}
