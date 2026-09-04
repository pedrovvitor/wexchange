package com.pedrolima.wexchange.adapter.out.fiscal;

import com.pedrolima.wexchange.domain.error.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct, boundary-exact tests for {@link HttpFiscalDataClient}'s pure helper
 * methods - package-visible specifically so these can be verified precisely
 * without a full HTTP round trip. {@link HttpFiscalDataClientIT} covers the
 * same logic end to end; this suite pins down the exact edges. Retry-policy
 * helpers moved to {@link FiscalResiliencePoliciesTest}.
 */
class HttpFiscalDataClientHelpersTest {

    // --- isSameOrigin / effectivePort ---------------------------------------

    @Test
    void givenIdenticalSchemeHostAndPort_whenComparingOrigins_thenTheyMatch() {
        assertTrue(HttpFiscalDataClient.isSameOrigin(
                URI.create("http://example.com:8080/a"), URI.create("http://example.com:8080/b")));
    }

    @Test
    void givenDifferentHosts_whenComparingOrigins_thenTheyDoNotMatch() {
        assertFalse(HttpFiscalDataClient.isSameOrigin(
                URI.create("http://example.com/a"), URI.create("http://evil.example.com/a")));
    }

    @Test
    void givenSameHostButDifferentPorts_whenComparingOrigins_thenTheyDoNotMatch() {
        assertFalse(HttpFiscalDataClient.isSameOrigin(
                URI.create("http://example.com:8080/a"), URI.create("http://example.com:9090/a")));
    }

    @Test
    void givenDifferentSchemes_whenComparingOrigins_thenTheyDoNotMatch() {
        assertFalse(HttpFiscalDataClient.isSameOrigin(
                URI.create("http://example.com/a"), URI.create("https://example.com/a")));
    }

    @Test
    void givenNoExplicitPort_whenComparingOrigins_thenTheSchemesDefaultPortIsUsed() {
        assertTrue(HttpFiscalDataClient.isSameOrigin(
                URI.create("http://example.com/a"), URI.create("http://example.com:80/a")));
        assertTrue(HttpFiscalDataClient.isSameOrigin(
                URI.create("https://example.com/a"), URI.create("https://example.com:443/a")));
        assertFalse(HttpFiscalDataClient.isSameOrigin(
                URI.create("https://example.com/a"), URI.create("https://example.com:80/a")));
    }

    @Test
    void givenAnExplicitPort_whenComputingTheEffectivePort_thenItIsUsedVerbatim() {
        assertEquals(8080, HttpFiscalDataClient.effectivePort(URI.create("http://example.com:8080/")));
    }

    // --- parseRetryAfterValue ------------------------------------------------

    @Test
    void givenAPositiveIntegerRetryAfter_whenParsing_thenItBecomesThatManySeconds() {
        assertEquals(Duration.ofSeconds(30), HttpFiscalDataClient.parseRetryAfterValue("30"));
    }

    @Test
    void givenAZeroRetryAfter_whenParsing_thenItIsZeroDuration() {
        assertEquals(Duration.ZERO, HttpFiscalDataClient.parseRetryAfterValue("0"));
    }

    @Test
    void givenANegativeRetryAfter_whenParsing_thenItIsRejectedAsNull() {
        assertNull(HttpFiscalDataClient.parseRetryAfterValue("-1"));
    }

    @Test
    void givenAnUnparsableRetryAfter_whenParsing_thenItIsNull() {
        assertNull(HttpFiscalDataClient.parseRetryAfterValue("whenever"));
    }

    // --- toRetryableException -------------------------------------------------

    @Test
    void givenAnUnderlyingFailure_whenWrappingItForTheCaller_thenTheMessageNamesItAndPreservesTheCause() {
        final var cause = new UpstreamIoException(new IOException("connection reset"));

        final RetryableException wrapped = HttpFiscalDataClient.toRetryableException(cause);

        assertTrue(wrapped.getMessage().contains("UpstreamIoException"));
        assertEquals(cause, wrapped.getCause());
    }

    @Test
    void givenACircuitBreakerRejection_whenWrappingItForTheCaller_thenTheMessageSaysSo() {
        final var cause = CallNotPermittedException.createCallNotPermittedException(
                io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("test"));

        final RetryableException wrapped = HttpFiscalDataClient.toRetryableException(cause);

        assertTrue(wrapped.getMessage().contains("CallNotPermittedException"));
    }
}
