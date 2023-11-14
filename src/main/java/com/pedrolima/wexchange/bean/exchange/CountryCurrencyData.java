package com.pedrolima.wexchange.bean.exchange;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CountryCurrencyData(
        @JsonProperty("country_currency_desc") String countryCurrencyDesc,
        @JsonProperty("country") String country,
        @JsonProperty("currency") String currency
) {

}
