package com.pedrolima.wexchange.adapter.in.web;

import com.pedrolima.wexchange.domain.error.PayloadTooLargeException;
import com.pedrolima.wexchange.domain.error.RateLimitExceededException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link GlobalExceptionHandler#handleConstraintViolation} is exercised here
 * directly rather than through {@code PurchaseApiErrorContractIT}: standalone
 * MockMvc does not build the AOP proxy that {@code @Validated} on
 * {@link PurchaseController} needs, so a query-parameter constraint violation
 * cannot be triggered end to end in that suite. The handler itself has no such
 * dependency, so it is tested on its own.
 */
class GlobalExceptionHandlerTest {

    @Test
    @DisplayName("a constraint violation is reported as a 400 problem listing every violated field")
    void givenConstraintViolation_whenHandling_thenViolationsAreReported() {
        final var handler = new GlobalExceptionHandler();
        final var request = new ServletWebRequest(new MockHttpServletRequest("GET", "/v1/purchases/1/convert"));

        @SuppressWarnings("unchecked")
        final ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        final Path path = mock(Path.class);
        when(path.toString()).thenReturn("convertPurchase.countryCurrency");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must not be blank");

        final var problemDetail = handler.handleConstraintViolation(
                new ConstraintViolationException(Set.of(violation)), request);

        assertEquals(400, problemDetail.getStatus());
        assertEquals("validation-failed", problemDetail.getProperties().get("code"));
        assertEquals(
                List.of(new GlobalExceptionHandler.Violation("convertPurchase.countryCurrency", "must not be blank")),
                problemDetail.getProperties().get("violations"));
    }

    @Test
    @DisplayName("a rate-limit rejection is reported as 429 carrying a Retry-After header")
    void givenRateLimitExceeded_whenHandling_then429WithRetryAfter() {
        final var handler = new GlobalExceptionHandler();
        final var request = new ServletWebRequest(new MockHttpServletRequest("POST", "/v1/purchases"));

        final var response = handler.handleRateLimitExceeded(
                new RateLimitExceededException("Rate limit exceeded for PURCHASE_CREATION", 42), request);

        assertEquals(429, response.getStatusCode().value());
        assertEquals("42", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals("rate-limit-exceeded", response.getBody().getProperties().get("code"));
        assertEquals("Rate limit exceeded for PURCHASE_CREATION", response.getBody().getDetail());
    }

    @Test
    @DisplayName("an oversized payload is reported as 413")
    void givenPayloadTooLarge_whenHandling_then413() {
        final var handler = new GlobalExceptionHandler();
        final var request = new ServletWebRequest(new MockHttpServletRequest("POST", "/v1/purchases"));

        final var problemDetail = handler.handlePayloadTooLarge(
                new PayloadTooLargeException("Request body exceeds the maximum allowed size of 16384 bytes"), request);

        assertEquals(413, problemDetail.getStatus());
        assertEquals("payload-too-large", problemDetail.getProperties().get("code"));
    }
}
