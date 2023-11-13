package com.pedrolima.wexchange.util;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class CustomLocalDateDeserializer extends JsonDeserializer<LocalDate> {

    @Override
    public LocalDate deserialize(final JsonParser parser, final DeserializationContext context) throws IOException, JacksonException {
        final var dateStr = parser.getText();
        if (dateStr == null) {
            return null;
        }

        if (dateStr.trim().isEmpty()) {
            throw new DateTimeParseException("Date string must not be empty", dateStr, 0);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        try {
            return LocalDate.parse(dateStr, formatter);
        } catch (DateTimeParseException e) {
            throw new DateTimeParseException("Date string is not in 'YYYY-mm-dd' format", dateStr, 0);
        }
    }
}
