package com.pedrolima.wexchange.bean.exchange;

import com.pedrolima.wexchange.api.ApiLink;
import com.pedrolima.wexchange.integration.fiscal.bean.CountryCurrency;
import org.springframework.data.domain.Page;

import java.util.List;

public record CountryCurrencyOutput(
        Page<CountryCurrency> countryCurrencies,
        List<ApiLink> links

) {

    public static CountryCurrencyOutput with(Page<CountryCurrency> countryCurrencies, List<ApiLink> links) {
        return new CountryCurrencyOutput(countryCurrencies, links);
    }
}
