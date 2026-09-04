package com.pedrolima.wexchange.adapter.out.fiscal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpHeaders;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpstreamExceptionsTest {

    @Test
    void givenAStatusException_whenReadingItsFields_thenTheyMatchWhatWasConstructed() {
        final var exception = new UpstreamHttpStatusException(503, true, Duration.ofSeconds(5));

        assertEquals(503, exception.statusCode());
        assertTrue(exception.retryable());
        assertEquals(Duration.ofSeconds(5), exception.retryAfter());
    }

    @Test
    void givenANonRetryableStatusExceptionWithNoRetryAfter_whenReadingItsFields_thenTheyReflectThat() {
        final var exception = new UpstreamHttpStatusException(400, false, null);

        assertEquals(400, exception.statusCode());
        assertFalse(exception.retryable());
        assertNull(exception.retryAfter());
    }

    @Test
    void givenAnIoException_whenWrappedAsUpstreamIoException_thenTheCauseIsPreserved() {
        final var cause = new IOException("connection reset");

        final var wrapped = new UpstreamIoException(cause);

        assertEquals(cause, wrapped.getCause());
        assertEquals("connection reset", wrapped.getMessage());
    }

    @Test
    void givenAnInterruptedException_whenWrappedAsUpstreamInterruptedException_thenTheCauseIsPreserved() {
        final var cause = new InterruptedException();

        final var wrapped = new UpstreamInterruptedException(cause);

        assertEquals(cause, wrapped.getCause());
    }

    @Test
    void givenAResponseTooLargeException_whenReadingItsMessage_thenItNamesTheConfiguredCap() {
        final var exception = new ResponseTooLargeException(1024);

        assertTrue(exception.getMessage().contains("1024"));
    }

    @Test
    void givenHeadersWithARetryAfterValue_whenParsing_thenTheDurationIsExtracted() {
        final var headers = HttpHeaders.of(Map.of("Retry-After", java.util.List.of("15")), (a, b) -> true);

        assertEquals(Duration.ofSeconds(15), HttpFiscalDataClient.parseRetryAfter(headers));
    }

    @Test
    void givenHeadersWithNoRetryAfterValue_whenParsing_thenNullIsReturned() {
        final var headers = HttpHeaders.of(Map.of(), (a, b) -> true);

        assertNull(HttpFiscalDataClient.parseRetryAfter(headers));
    }
}
