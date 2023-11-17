package com.pedrolima.wexchange.integration.fiscal.beans;

import com.pedrolima.wexchange.entities.CountryCurrencyJpaEntity;

public record CountryCurrency(
        String countryCurrency,
        String country,
        String currency
) {

    public static CountryCurrency with(final CountryCurrencyJpaEntity CountryCurrency) {
        return new CountryCurrency(
                CountryCurrency.getCountryCurrency(),
                CountryCurrency.getCountry(),
                CountryCurrency.getCurrency()
        );
    }
}
