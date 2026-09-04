package com.pedrolima.wexchange.adapter.in.web;

import com.pedrolima.wexchange.adapter.out.persistence.CountryCurrencyJpaEntity;

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
