package com.pedrolima.wexchange.integration.fiscal.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pedrolima.wexchange.entities.CountryCurrencyJpaEntity;

public record CountryCurrency(
        @JsonProperty("country_currency_desc") String countryCurrency,
        @JsonProperty("country") String country,
        @JsonProperty("currency") String currency
) {

    public static CountryCurrency with(final CountryCurrencyJpaEntity CountryCurrency) {
        return new CountryCurrency(
                CountryCurrency.getCountryCurrency(),
                CountryCurrency.getCountry(),
                CountryCurrency.getCurrency()
        );
    }
}
