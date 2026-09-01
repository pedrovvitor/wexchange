package com.pedrolima.wexchange.service;

import com.pedrolima.wexchange.entities.CountryCurrencyJpaEntity;
import com.pedrolima.wexchange.integration.fiscal.beans.CountryCurrencyInput;
import com.pedrolima.wexchange.repositories.CountryCurrencyRepository;
import com.pedrolima.wexchange.services.CountryCurrencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The service chooses between listing every country-currency and filtering by a
 * search term. Picking the wrong branch silently returns the whole catalogue to
 * a caller that asked for one entry, so both directions are asserted.
 */
@ExtendWith(MockitoExtension.class)
class CountryCurrencyServiceTest {

    private static final Pageable PAGEABLE = PageRequest.of(0, 20);

    @Mock
    private CountryCurrencyRepository repository;

    @InjectMocks
    private CountryCurrencyService service;

    @ParameterizedTest
    @DisplayName("a blank search term lists the whole catalogue")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void givenBlankTerm_whenSearching_thenAllCountryCurrenciesAreListed(final String blankTerm) {
        when(repository.findAll(PAGEABLE)).thenReturn(pageOf("Brazil-Real", "Canada-Dollar"));

        final var output = service.findByCountryCurrency(PAGEABLE, blankTerm);

        assertEquals(2, output.countryCurrencies().getTotalElements());
        verify(repository).findAll(PAGEABLE);
        verify(repository, never()).findAllContainingCountryCurrencyIgnoreCase(PAGEABLE, blankTerm);
    }

    @Test
    @DisplayName("a search term filters the catalogue case-insensitively")
    void givenSearchTerm_whenSearching_thenTheFilteringQueryIsUsed() {
        when(repository.findAllContainingCountryCurrencyIgnoreCase(eq(PAGEABLE), eq("real")))
                .thenReturn(pageOf("Brazil-Real"));

        final var output = service.findByCountryCurrency(PAGEABLE, "real");

        assertEquals(1, output.countryCurrencies().getTotalElements());
        assertEquals("Brazil-Real", output.countryCurrencies().getContent().get(0).countryCurrency());
        verify(repository).findAllContainingCountryCurrencyIgnoreCase(PAGEABLE, "real");
        verify(repository, never()).findAll(PAGEABLE);
    }

    @Test
    @DisplayName("the response advertises the conversion endpoint and its parameters")
    void givenAnySearch_whenSearching_thenTheConversionLinkIsAdvertised() {
        when(repository.findAll(PAGEABLE)).thenReturn(pageOf("Brazil-Real"));

        final var output = service.findByCountryCurrency(PAGEABLE, null);

        assertEquals(1, output.links().size());
        final var link = output.links().get(0);
        assertEquals("convert", link.rel());
        assertEquals("GET", link.method());
        assertEquals("/v1/purchases/{id}/convert?country_currency=", link.href());
        assertTrue(link.params().containsKey("country_currency"));
        assertTrue(link.params().containsKey("{id}"));
    }

    @Test
    @DisplayName("an empty catalogue is reported as an empty page rather than as an error")
    void givenEmptyCatalogue_whenSearching_thenAnEmptyPageIsReturned() {
        when(repository.findAll(PAGEABLE)).thenReturn(new PageImpl<>(List.of(), PAGEABLE, 0));

        final var output = service.findByCountryCurrency(PAGEABLE, "  ");

        assertTrue(output.countryCurrencies().getContent().isEmpty());
    }

    private static Page<CountryCurrencyJpaEntity> pageOf(final String... countryCurrencies) {
        final var entities = List.of(countryCurrencies).stream()
                .map(name -> CountryCurrencyJpaEntity.with(
                        new CountryCurrencyInput(name, name.split("-")[0], name.split("-")[1])))
                .toList();
        return new PageImpl<>(entities, PAGEABLE, entities.size());
    }
}
