package com.pedrolima.wexchange.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CORS allowlist (issue #16 / ADR 0002): the one configured origin passes
 * preflight for the methods and headers the API actually needs, and every
 * other origin receives no CORS permission at all.
 *
 * <p>Uses {@link HttpClient} directly rather than {@code TestRestTemplate}:
 * the latter's default JDK {@code HttpURLConnection}-backed request factory
 * silently drops the {@code Origin} header as a restricted header, which
 * would make every one of these assertions pass for the wrong reason (the
 * server never saw an origin at all, not that it correctly rejected one).
 */
class CorsConfigurationIT extends AbstractPostgresApplicationIT {

    private static final String ALLOWED_ORIGIN = "https://allowed.example.com";
    private static final String UNTRUSTED_ORIGIN = "https://untrusted.example.com";

    private final HttpClient httpClient = HttpClient.newBuilder().build();

    @DynamicPropertySource
    static void corsProperties(final DynamicPropertyRegistry registry) {
        registry.add("app.cors.allowed-origins", () -> ALLOWED_ORIGIN);
    }

    @Test
    void givenTheConfiguredOrigin_whenPreflighting_thenItIsAllowedForTheRequiredMethodsAndHeaders()
            throws IOException, InterruptedException {
        final HttpResponse<Void> response = preflight(ALLOWED_ORIGIN, "POST", "Content-Type,Idempotency-Key");

        assertTrue(response.statusCode() >= 200 && response.statusCode() < 300);
        assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").filter(ALLOWED_ORIGIN::equals).isPresent());
        final String allowedHeaders = response.headers().firstValue("Access-Control-Allow-Headers").orElse("");
        assertTrue(allowedHeaders.toLowerCase().contains("content-type")
                && allowedHeaders.toLowerCase().contains("idempotency-key"));
        assertTrue(response.headers().firstValue("Access-Control-Allow-Credentials").isEmpty());
    }

    @Test
    void givenAnUntrustedOrigin_whenPreflighting_thenNoCorsPermissionIsGranted() throws IOException, InterruptedException {
        final HttpResponse<Void> response = preflight(UNTRUSTED_ORIGIN, "POST", "Content-Type");

        assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }

    @Test
    void givenAnUntrustedOrigin_whenMakingACrossOriginRequest_thenItIsRejectedRatherThanSilentlyPermitted()
            throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl("/actuator/health")))
                .header("Origin", UNTRUSTED_ORIGIN)
                .GET()
                .build();

        final HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

        // Spring's server-side CorsProcessor checks the origin for any
        // cross-origin request, preflighted or not, and rejects outright when
        // it doesn't match - stronger than leaving enforcement to the browser.
        assertTrue(response.statusCode() == 403);
        assertNull(response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
    }

    @Test
    void givenNoOriginHeaderAtAll_whenMakingARequest_thenItIsUnaffectedByCors() throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl("/actuator/health")))
                .GET()
                .build();

        final HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

        assertTrue(response.statusCode() == 200, "a same-origin / non-browser request must never be CORS-rejected");
    }

    private HttpResponse<Void> preflight(final String origin, final String requestMethod, final String requestHeaders)
            throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl("/v1/purchases")))
                .header("Origin", origin)
                .header("Access-Control-Request-Method", requestMethod)
                .header("Access-Control-Request-Headers", requestHeaders)
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    }
}
