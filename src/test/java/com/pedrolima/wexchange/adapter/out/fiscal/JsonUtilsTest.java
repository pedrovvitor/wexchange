package com.pedrolima.wexchange.adapter.out.fiscal;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Upstream payloads reach the application through this helper, so its failure
 * behaviour matters as much as its happy path: a malformed body must raise, not
 * quietly yield an empty list that looks like "no data upstream".
 */
class JsonUtilsTest {

    @Test
    @DisplayName("extracts the data array into the requested type")
    void givenPayloadWithData_whenExtracting_thenElementsAreMapped() throws IOException {
        final var json = """
                {"data":[
                  {"country_currency_desc":"Brazil-Real","country":"Brazil","currency":"Real"},
                  {"country_currency_desc":"Canada-Dollar","country":"Canada","currency":"Dollar"}
                ]}
                """;

        final var extracted = JsonUtils.extractDataList(json, CountryCurrencyInput.class);

        assertEquals(2, extracted.size());
        assertEquals("Brazil-Real", extracted.get(0).countryCurrency());
        assertEquals("Canada", extracted.get(1).country());
    }

    @Test
    @DisplayName("ignores properties the application does not model")
    void givenUnknownProperties_whenExtracting_thenTheyAreIgnored() throws IOException {
        final var json = """
                {"meta":{"count":1},"data":[
                  {"country_currency_desc":"Brazil-Real","country":"Brazil","currency":"Real","unmapped":"x"}
                ]}
                """;

        final var extracted = JsonUtils.extractDataList(json, CountryCurrencyInput.class);

        assertEquals(1, extracted.size());
        assertEquals("Real", extracted.get(0).currency());
    }

    @Test
    @DisplayName("an empty data array yields an empty list")
    void givenEmptyDataArray_whenExtracting_thenListIsEmpty() throws IOException {
        final var extracted = JsonUtils.extractDataList("{\"data\":[]}", CountryCurrencyInput.class);

        assertTrue(extracted.isEmpty());
    }

    /**
     * Current behaviour, deliberately pinned: a response with no {@code data}
     * node produces an empty string for Jackson to read, which raises rather
     * than returning an empty list. Callers therefore treat a shape change
     * upstream as a retryable parsing failure instead of as "no rates today".
     * Issue #3 owns whether that is the response the client should give.
     */
    @Test
    @DisplayName("a payload without a data node raises rather than looking like an empty result")
    void givenPayloadWithoutData_whenExtracting_thenItRaises() {
        assertThrows(JsonProcessingException.class,
                () -> JsonUtils.extractDataList("{\"meta\":{\"count\":0}}", CountryCurrencyInput.class));
    }

    @Test
    @DisplayName("a malformed payload raises instead of returning nothing")
    void givenMalformedPayload_whenExtracting_thenItRaises() {
        assertThrows(JsonProcessingException.class,
                () -> JsonUtils.extractDataList("{\"data\":[", CountryCurrencyInput.class));
    }
}
