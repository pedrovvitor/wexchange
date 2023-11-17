package com.pedrolima.wexchange.integration.fiscal.beans;

import com.pedrolima.wexchange.api.ApiLink;
import org.springframework.data.domain.Page;

import java.util.List;

public record CountryCurrencyOutput(
        Page<CountryCurrency> countryCurrencies,
        List<ApiLink> links

) {

    public static CountryCurrencyOutput with(final Page<CountryCurrency> countryCurrencies, final List<ApiLink> links) {
        return new CountryCurrencyOutput(countryCurrencies, links);
    }
}
