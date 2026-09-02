package com.pedrolima.wexchange.domain.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The messages these carry reach callers verbatim in the error body, and the
 * cause is what makes an upstream failure diagnosable. Both are contract.
 */
class DomainErrorTest {

    @Test
    @DisplayName("each error carries its message through to the caller")
    void givenMessage_whenConstructing_thenItIsPreserved() {
        assertEquals("not found", new ResourceNotFoundException("not found").getMessage());
        assertEquals("no rate", new ExchangeRateNotFoundException("no rate").getMessage());
        assertEquals("ambiguous", new MultipleCountryCurrenciesException("ambiguous").getMessage());
        assertEquals("cannot convert", new PurchaseConversionException("cannot convert").getMessage());
        assertEquals("upstream down", new RetryableException("upstream down").getMessage());
    }

    @Test
    @DisplayName("a retryable failure can carry the upstream cause that produced it")
    void givenCause_whenConstructingRetryable_thenTheCauseIsPreserved() {
        final var cause = new IOException("connection reset");

        final var thrown = new RetryableException("upstream down", cause);

        assertEquals("upstream down", thrown.getMessage());
        assertSame(cause, thrown.getCause());
    }
}
