package com.pedrolima.wexchange.entities;

import com.pedrolima.wexchange.integration.fiscal.bean.CountryCurrency;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CountryCurrencyJpaEntityTest {

    @Test
    void givenValidCountryCurrency_whenCallWith_thenInstantiateCountryCurrencyJpaEntity() {
        final var countryCurrency = "Brazil-Real";
        final var country = "Brazil";
        final var currency = "Real";

        CountryCurrencyJpaEntity countryCurrencyJpaEntity = CountryCurrencyJpaEntity.with(
                new CountryCurrency(countryCurrency, country, currency));

        assertEquals(countryCurrency, countryCurrencyJpaEntity.getCountryCurrency());
        assertEquals(country, countryCurrencyJpaEntity.getCountry());
        assertEquals(currency, countryCurrencyJpaEntity.getCurrency());
    }
}

