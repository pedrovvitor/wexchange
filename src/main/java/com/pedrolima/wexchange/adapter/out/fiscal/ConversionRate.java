package com.pedrolima.wexchange.adapter.out.fiscal;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ConversionRate(
        @JsonProperty("exchange_rate") BigDecimal exchangeRate,
        @JsonProperty("effective_date") LocalDate effectiveDate,
        @JsonProperty("country_currency_desc") String countryCurrency
) {
}
