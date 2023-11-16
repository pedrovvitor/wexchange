package com.pedrolima.wexchange.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.pedrolima.wexchange.exceptions.DeserializationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Slf4j
public class CustomLocalDateDeserializer extends JsonDeserializer<LocalDate> {

    @Override
    public LocalDate deserialize(final JsonParser parser, final DeserializationContext context) {
        final String dateStr;
        try {
            dateStr = parser.getText();
            if (StringUtils.isBlank(dateStr)) {
                throw new DeserializationException("Date must not be blank");
            }
            return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (IOException e) {
            log.error("Unable to parse {} {}", parser, e.toString());
            throw new DeserializationException("Unable to parse 'date' input");
        } catch (DateTimeParseException e) {
            log.error("Unable to parse {} {}", parser, e.toString());
            throw new DeserializationException(e.getMessage());
        }
    }
}
