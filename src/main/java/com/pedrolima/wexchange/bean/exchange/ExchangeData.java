package com.pedrolima.wexchange.bean.exchange;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record ExchangeData(
        @JsonProperty("country") String country,
        @JsonProperty("currency") String currency,
        @JsonProperty("exchange_rate") BigDecimal exchangeRate,
        @JsonProperty("effective_date") String effectiveDate
) {

}
