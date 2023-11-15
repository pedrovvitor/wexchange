package com.pedrolima.wexchange.bean.exchange;

import com.pedrolima.wexchange.api.ApiLink;
import com.pedrolima.wexchange.integration.fiscal.bean.CountryCurrency;

import java.util.List;

public record CountryCurrencyOutput(
        List<CountryCurrency> countryCurrencies,
        List<ApiLink> links

) {

    public static CountryCurrencyOutput with(List<CountryCurrency> countryCurrencies, List<ApiLink> links) {
        return new CountryCurrencyOutput(countryCurrencies, links);
    }
}
