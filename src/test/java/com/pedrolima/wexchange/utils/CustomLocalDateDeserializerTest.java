package com.pedrolima.wexchange.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.pedrolima.wexchange.exceptions.DeserializationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Purchase dates arrive as untrusted client input. Every rejection path must
 * surface as {@link DeserializationException} so the inbound adapter can answer
 * 400 rather than leaking a parser stack trace as a 500.
 */
class CustomLocalDateDeserializerTest {

    private final CustomLocalDateDeserializer deserializer = new CustomLocalDateDeserializer();

    @Test
    @DisplayName("parses an ISO-8601 local date")
    void givenIsoDate_whenDeserializing_thenItIsParsed() throws IOException {
        final var parser = parserReturning("2024-01-31");

        assertEquals(LocalDate.of(2024, 1, 31), deserializer.deserialize(parser, null));
    }

    @ParameterizedTest
    @DisplayName("a blank date is rejected")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void givenBlankDate_whenDeserializing_thenItIsRejected(final String blank) throws IOException {
        final var parser = parserReturning(blank);

        final var thrown = assertThrows(DeserializationException.class,
                () -> deserializer.deserialize(parser, null));

        assertEquals("Date must not be blank", thrown.getMessage());
    }

    @ParameterizedTest
    @DisplayName("a date that is not ISO-8601 is rejected")
    @ValueSource(strings = {"31-01-2024", "2024/01/31", "2024-13-01", "2023-02-29", "not-a-date"})
    void givenNonIsoDate_whenDeserializing_thenItIsRejected(final String value) throws IOException {
        final var parser = parserReturning(value);

        assertThrows(DeserializationException.class, () -> deserializer.deserialize(parser, null));
    }

    @Test
    @DisplayName("a parser I/O failure is reported as a deserialization failure, not propagated raw")
    void givenParserIoFailure_whenDeserializing_thenItIsReportedAsDeserializationFailure() throws IOException {
        final var parser = mock(JsonParser.class);
        when(parser.getText()).thenThrow(new IOException("stream closed"));

        final var thrown = assertThrows(DeserializationException.class,
                () -> deserializer.deserialize(parser, null));

        assertEquals("Unable to parse 'date' input", thrown.getMessage());
    }

    private static JsonParser parserReturning(final String text) throws IOException {
        final var parser = mock(JsonParser.class);
        when(parser.getText()).thenReturn(text);
        return parser;
    }
}
