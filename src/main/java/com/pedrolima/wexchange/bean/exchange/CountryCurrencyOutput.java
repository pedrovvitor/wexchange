package com.pedrolima.wexchange.bean.exchange;

import com.pedrolima.wexchange.api.ApiLink;

import java.util.List;

public record CountryCurrencyOutput(
        List<CountryCurrencyData> countryCurrencies,
        List<ApiLink> links

) {
    public static CountryCurrencyOutput with (List<CountryCurrencyData> countryCurrencies, List<ApiLink> links) {
        return new CountryCurrencyOutput(countryCurrencies, links);
    }
}
