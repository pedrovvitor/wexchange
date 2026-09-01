package com.pedrolima.wexchange.integration.fiscal.beans;

import com.pedrolima.wexchange.entities.CountryCurrencyJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mapping between the persistence entity and the two boundary representations.
 * The three fields are same-typed strings, so a transposed argument compiles
 * happily and only shows up as a wrong country in an API response.
 */
class CountryCurrencyMappingTest {

    private static final CountryCurrencyJpaEntity ENTITY = CountryCurrencyJpaEntity.with(
            new CountryCurrencyInput("Brazil-Real", "Brazil", "Real"));

    @Test
    @DisplayName("the API representation keeps each field in its own slot")
    void givenEntity_whenMappingToApiRepresentation_thenFieldsAreNotTransposed() {
        final var mapped = CountryCurrency.with(ENTITY);

        assertEquals("Brazil-Real", mapped.countryCurrency());
        assertEquals("Brazil", mapped.country());
        assertEquals("Real", mapped.currency());
    }

    @Test
    @DisplayName("the upstream representation keeps each field in its own slot")
    void givenEntity_whenMappingToUpstreamRepresentation_thenFieldsAreNotTransposed() {
        final var mapped = CountryCurrencyInput.with(ENTITY);

        assertEquals("Brazil-Real", mapped.countryCurrency());
        assertEquals("Brazil", mapped.country());
        assertEquals("Real", mapped.currency());
    }

    @Test
    @DisplayName("mapping to the entity and back is lossless")
    void givenUpstreamPayload_whenRoundTripping_thenNothingIsLost() {
        final var original = new CountryCurrencyInput("Canada-Dollar", "Canada", "Dollar");

        final var roundTripped = CountryCurrencyInput.with(CountryCurrencyJpaEntity.with(original));

        assertEquals(original, roundTripped);
    }
}
