package com.pedrolima.wexchange.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end against a real Postgres and the real application context (issue
 * #17): the structural abuse controls - request-body size, page size, sort
 * allowlist, filter length - rather than rate limiting itself, which needs
 * tight, easily-exhausted limits and therefore lives in its own test class
 * ({@link RateLimitIT}) with its own isolated Spring context, so the two
 * concerns' requests never share a bucket and cross-contaminate each other's
 * assertions. Every limit here is deliberately overridden to be generous, so
 * a request this suite expects to succeed never fails on rate-limit grounds
 * instead of the thing actually being tested.
 */
class AbuseControlIT extends AbstractPostgresApplicationIT {

    @DynamicPropertySource
    static void generousRateLimits(final DynamicPropertyRegistry registry) {
        registry.add("app.abuse-control.global.capacity", () -> "10000");
        registry.add("app.abuse-control.global.refill-tokens", () -> "10000");
        registry.add("app.abuse-control.purchase-creation.capacity", () -> "10000");
        registry.add("app.abuse-control.purchase-creation.refill-tokens", () -> "10000");
        registry.add("app.abuse-control.conversion.capacity", () -> "10000");
        registry.add("app.abuse-control.conversion.refill-tokens", () -> "10000");
        registry.add("app.abuse-control.country-currencies.capacity", () -> "10000");
        registry.add("app.abuse-control.country-currencies.refill-tokens", () -> "10000");
    }

    @Test
    void givenARequestBodyOverTheConfiguredLimit_whenCreatingAPurchase_then413() {
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        final String oversizedBody = "{\"description\":\"" + "x".repeat(20_000) + "\",\"date\":\"2024-01-31\",\"amount\":10.00}";

        final ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl("/v1/purchases"), new HttpEntity<>(oversizedBody, headers), String.class);

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertTrue(response.getBody().contains("payload-too-large"));
    }

    @Test
    void givenARequestBodyWithinTheConfiguredLimit_whenCreatingAPurchase_thenItProceeds() {
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        final String body = "{\"description\":\"A normal purchase\",\"date\":\"2024-01-31\",\"amount\":10.00}";

        final ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl("/v1/purchases"), new HttpEntity<>(body, headers), String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void givenAPageSizeOverTheConfiguredMaximum_whenListingCountryCurrencies_then400() {
        final ResponseEntity<String> response =
                restTemplate.getForEntity(baseUrl("/v1/country_currencies?size=1000"), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("invalid-argument"));
    }

    @Test
    void givenAPageSizeWithinTheConfiguredMaximum_whenListingCountryCurrencies_thenItProceeds() {
        final ResponseEntity<String> response =
                restTemplate.getForEntity(baseUrl("/v1/country_currencies?size=50"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void givenAnUnknownSortProperty_whenListingCountryCurrencies_then400WithoutAnUnboundedQuery() {
        final ResponseEntity<String> response =
                restTemplate.getForEntity(baseUrl("/v1/country_currencies?sort=neverAColumn,asc"), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("invalid-argument"));
    }

    @Test
    void givenAnAllowedSortProperty_whenListingCountryCurrencies_thenItProceeds() {
        final ResponseEntity<String> response =
                restTemplate.getForEntity(baseUrl("/v1/country_currencies?sort=country,asc"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void givenAnOverlongCountryCurrencyFilter_whenListingCountryCurrencies_then400() {
        final String overlongFilter = "a".repeat(101);

        final ResponseEntity<String> response =
                restTemplate.getForEntity(baseUrl("/v1/country_currencies?country_currency=" + overlongFilter), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("invalid-argument"));
    }
}
