package com.pedrolima.wexchange.integration.fiscal.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pedrolima.wexchange.entities.CountryCurrencyJpaEntity;

public record CountryCurrencyInput(
        @JsonProperty("country_currency_desc") String countryCurrency,
        @JsonProperty("country") String country,
        @JsonProperty("currency") String currency
) {

    public static CountryCurrencyInput with(final CountryCurrencyJpaEntity CountryCurrency) {
        return new CountryCurrencyInput(
                CountryCurrency.getCountryCurrency(),
                CountryCurrency.getCountry(),
                CountryCurrency.getCurrency()
        );
    }
}
