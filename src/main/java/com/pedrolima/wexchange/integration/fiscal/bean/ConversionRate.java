package com.pedrolima.wexchange.integration.fiscal.bean;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ConversionRate(
        @JsonProperty("exchange_rate") BigDecimal exchangeRate,
        @JsonProperty("effective_date") LocalDate effectiveDate,
        @JsonProperty("country_currency_desc") String countryCurrency
) {

    public static ConversionRate with(
            final BigDecimal anExchangeRate,
            final LocalDate anEffectiveDate,
            final String aCountryCurrency) {
        return new ConversionRate(anExchangeRate, anEffectiveDate, aCountryCurrency);
    }
}
