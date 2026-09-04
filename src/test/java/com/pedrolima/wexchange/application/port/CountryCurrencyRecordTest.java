package com.pedrolima.wexchange.application.port;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CountryCurrencyRecordTest {

    @Test
    void givenValidFields_whenConstructed_thenTheyAreKept() {
        final var record = new CountryCurrencyRecord("Brazil-Real", "Brazil", "Real");

        assertEquals("Brazil-Real", record.countryCurrency());
        assertEquals("Brazil", record.country());
        assertEquals("Real", record.currency());
    }

    @Test
    void givenANullCountryCurrency_whenConstructed_thenItIsRejected() {
        assertThrows(NullPointerException.class, () -> new CountryCurrencyRecord(null, "Brazil", "Real"));
    }

    @Test
    void givenANullCountry_whenConstructed_thenItIsRejected() {
        assertThrows(NullPointerException.class, () -> new CountryCurrencyRecord("Brazil-Real", null, "Real"));
    }

    @Test
    void givenANullCurrency_whenConstructed_thenItIsRejected() {
        assertThrows(NullPointerException.class, () -> new CountryCurrencyRecord("Brazil-Real", "Brazil", null));
    }

    @Test
    void givenABlankField_whenConstructed_thenItIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CountryCurrencyRecord(" ", "Brazil", "Real"));
        assertThrows(IllegalArgumentException.class, () -> new CountryCurrencyRecord("Brazil-Real", " ", "Real"));
        assertThrows(IllegalArgumentException.class, () -> new CountryCurrencyRecord("Brazil-Real", "Brazil", " "));
    }
}
