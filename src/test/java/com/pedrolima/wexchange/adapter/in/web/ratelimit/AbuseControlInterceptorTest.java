package com.pedrolima.wexchange.adapter.in.web.ratelimit;

import com.pedrolima.wexchange.adapter.in.web.ratelimit.AbuseControlProperties.RouteLimit;
import com.pedrolima.wexchange.domain.error.PayloadTooLargeException;
import com.pedrolima.wexchange.domain.error.RateLimitExceededException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AbuseControlInterceptor} against a real {@link PerKeyRateLimiter}
 * (itself already unit-tested) and a real {@link AbuseControlMetrics} backed
 * by a {@link SimpleMeterRegistry} - only the interceptor's own dispatch
 * decisions (which check applies to which route, in what order) are the
 * subject here.
 */
class AbuseControlInterceptorTest {

    /** Deliberately tiny limits so a second identical call always exhausts a bucket. */
    private static final AbuseControlProperties PROPERTIES = new AbuseControlProperties(
            true,
            new RouteLimit(2, 10, Duration.ofMinutes(1)),
            new RouteLimit(1, 10, Duration.ofMinutes(1)),
            new RouteLimit(1, 10, Duration.ofMinutes(1)),
            new RouteLimit(1, 10, Duration.ofMinutes(1)),
            16,
            100,
            100);

    private final MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));

    private final AbuseControlInterceptor interceptor = new AbuseControlInterceptor(
            PROPERTIES, new PerKeyRateLimiter(clock), new AbuseControlMetrics(new SimpleMeterRegistry()));

    @Test
    @DisplayName("a purchase-creation request within the body-size limit proceeds")
    void givenAPurchaseCreationRequestWithinTheBodySizeLimit_whenHandling_thenItProceeds() {
        final var request = new MockHttpServletRequest("POST", "/v1/purchases");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    @DisplayName("a purchase-creation request exceeding the body-size limit is rejected before proceeding")
    void givenAPurchaseCreationRequestExceedingTheBodySizeLimit_whenHandling_thenPayloadTooLargeIsThrown() {
        final var request = new MockHttpServletRequest("POST", "/v1/purchases");
        request.setContent(new byte[17]);

        assertThrows(PayloadTooLargeException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    @DisplayName("a country-currency request with an oversized page is rejected")
    void givenACountryCurrenciesRequestWithAnOversizedPage_whenHandling_thenItIsRejected() {
        final var request = new MockHttpServletRequest("GET", "/v1/country_currencies");
        request.setParameter("size", "101");

        assertThrows(IllegalArgumentException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    @DisplayName("a country-currency request with an allowed sort field proceeds")
    void givenACountryCurrenciesRequestWithAnAllowedSortField_whenHandling_thenItProceeds() {
        final var request = new MockHttpServletRequest("GET", "/v1/country_currencies");
        request.setParameter("sort", "country,asc");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    @DisplayName("a country-currency request with an unknown sort field is rejected before an unbounded query runs")
    void givenACountryCurrenciesRequestWithAnUnknownSortField_whenHandling_thenItIsRejected() {
        final var request = new MockHttpServletRequest("GET", "/v1/country_currencies");
        request.setParameter("sort", "unknownField,asc");

        assertThrows(IllegalArgumentException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    @DisplayName("a country-currency filter longer than the configured maximum is rejected")
    void givenACountryCurrenciesRequestWithAnOverlongFilter_whenHandling_thenItIsRejected() {
        final var request = new MockHttpServletRequest("GET", "/v1/country_currencies");
        request.setParameter("country_currency", "a".repeat(101));

        assertThrows(IllegalArgumentException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    @DisplayName("exhausting a route's own budget is reported with a positive retry-after")
    void givenTheRouteLimitIsExhausted_whenHandlingAgain_thenRateLimitExceededIsThrownWithARetryAfter() {
        final var first = new MockHttpServletRequest("GET", "/v1/country_currencies");
        interceptor.preHandle(first, new MockHttpServletResponse(), new Object());

        final var second = new MockHttpServletRequest("GET", "/v1/country_currencies");
        final var thrown = assertThrows(RateLimitExceededException.class,
                () -> interceptor.preHandle(second, new MockHttpServletResponse(), new Object()));

        assertTrue(thrown.retryAfterSeconds() > 0);
    }

    @Test
    @DisplayName("disabling rate limiting lets a caller exceed what would otherwise be its budget")
    void givenRateLimitingIsDisabled_whenExceedingTheWouldBeLimit_thenItStillProceeds() {
        final var disabled = new AbuseControlProperties(
                false, PROPERTIES.global(), PROPERTIES.purchaseCreation(), PROPERTIES.conversion(),
                PROPERTIES.countryCurrencies(), PROPERTIES.maxRequestBodyBytes(), PROPERTIES.maxPageSize(),
                PROPERTIES.maxCountryCurrencyFilterLength());
        final var disabledInterceptor = new AbuseControlInterceptor(
                disabled, new PerKeyRateLimiter(clock), new AbuseControlMetrics(new SimpleMeterRegistry()));

        for (int i = 0; i < 5; i++) {
            final var request = new MockHttpServletRequest("GET", "/v1/country_currencies");
            assertTrue(disabledInterceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        }
    }

    @Test
    @DisplayName("two different callers never share a budget")
    void givenDifferentClientAddresses_whenOneExhaustsItsLimit_thenTheOtherIsUnaffected() {
        final var first = new MockHttpServletRequest("GET", "/v1/country_currencies");
        first.setRemoteAddr("1.2.3.4");
        interceptor.preHandle(first, new MockHttpServletResponse(), new Object());
        final var firstAgain = new MockHttpServletRequest("GET", "/v1/country_currencies");
        firstAgain.setRemoteAddr("1.2.3.4");
        assertThrows(RateLimitExceededException.class,
                () -> interceptor.preHandle(firstAgain, new MockHttpServletResponse(), new Object()));

        final var second = new MockHttpServletRequest("GET", "/v1/country_currencies");
        second.setRemoteAddr("5.6.7.8");
        assertTrue(interceptor.preHandle(second, new MockHttpServletResponse(), new Object()));
    }

    @Test
    @DisplayName("a non-numeric page size is left for Spring Data's own binding to reject")
    void givenANonNumericPageSize_whenHandling_thenItIsNotThisInterceptorsConcern() {
        final var request = new MockHttpServletRequest("GET", "/v1/country_currencies");
        request.setParameter("size", "not-a-number");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    @DisplayName("the conversion route has its own budget, independent of the country-currencies route")
    void givenTheConversionRouteLimitIsExhausted_whenHandlingAgain_thenItIsRejected() {
        final var first = new MockHttpServletRequest("GET", "/v1/purchases/6e2b8a5d-3f17-4c90-a4e6-70d5c1b8f293/convert");
        interceptor.preHandle(first, new MockHttpServletResponse(), new Object());

        final var second = new MockHttpServletRequest("GET", "/v1/purchases/6e2b8a5d-3f17-4c90-a4e6-70d5c1b8f293/convert");
        assertThrows(RateLimitExceededException.class,
                () -> interceptor.preHandle(second, new MockHttpServletResponse(), new Object()));
    }

    @Test
    @DisplayName("a route with no specific budget still counts against the global limit")
    void givenAnUnlistedRoute_whenExceedingTheGlobalLimit_thenItIsRejected() {
        final var first = new MockHttpServletRequest("GET", "/v1/purchases/some-id");
        interceptor.preHandle(first, new MockHttpServletResponse(), new Object());
        final var second = new MockHttpServletRequest("GET", "/v1/purchases/some-id");
        interceptor.preHandle(second, new MockHttpServletResponse(), new Object());

        final var third = new MockHttpServletRequest("GET", "/v1/purchases/some-id");
        assertThrows(RateLimitExceededException.class,
                () -> interceptor.preHandle(third, new MockHttpServletResponse(), new Object()));
    }
}
