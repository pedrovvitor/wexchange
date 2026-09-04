package com.pedrolima.wexchange.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end against a real Postgres and the real application context (issue
 * #17): proves the wired-together stack - {@code AbuseControlInterceptor},
 * {@code PerKeyRateLimiter}, and {@code GlobalExceptionHandler} - actually
 * produces a {@code 429} with a {@code Retry-After} header once a route's
 * real HTTP budget is exhausted.
 *
 * <p>Kept in its own test class, isolated from {@link AbuseControlIT}: every
 * request in a {@code TestRestTemplate}-driven suite originates from the same
 * address, so every test method in a class sharing one Spring context also
 * shares one bucket per route. This class exists to exhaust a bucket on
 * purpose; a suite of otherwise-unrelated assertions must never share a
 * context with it, or an earlier test's consumed tokens would make a later,
 * unrelated assertion fail on rate-limit grounds instead of what it actually
 * tests. A single test method is deliberate for the same reason: a second
 * method in this same class would start with the bucket this one already
 * exhausted.
 */
class RateLimitIT extends AbstractPostgresApplicationIT {

    @DynamicPropertySource
    static void tightCountryCurrenciesLimit(final DynamicPropertyRegistry registry) {
        registry.add("app.abuse-control.global.capacity", () -> "1000");
        registry.add("app.abuse-control.global.refill-tokens", () -> "1000");
        registry.add("app.abuse-control.country-currencies.capacity", () -> "2");
        registry.add("app.abuse-control.country-currencies.refill-tokens", () -> "2");
        registry.add("app.abuse-control.country-currencies.refill-period", () -> "1m");
    }

    @Test
    void givenMoreRequestsThanTheRouteCapacity_whenListingCountryCurrencies_thenTheExcessIsRejectedWith429() {
        for (int i = 0; i < 2; i++) {
            final ResponseEntity<String> withinBudget = restTemplate.getForEntity(baseUrl("/v1/country_currencies"), String.class);
            assertEquals(HttpStatus.OK, withinBudget.getStatusCode());
        }

        final ResponseEntity<String> overBudget = restTemplate.getForEntity(baseUrl("/v1/country_currencies"), String.class);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, overBudget.getStatusCode());
        assertTrue(overBudget.getBody().contains("rate-limit-exceeded"));
        final String retryAfter = overBudget.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        assertNotNull(retryAfter, "a 429 must always carry a Retry-After header");
        assertTrue(Long.parseLong(retryAfter) > 0);
    }
}
