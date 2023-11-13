package com.pedrolima.wexchange.bean.exchange;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record ExchangeRateData(
        @JsonProperty("exchange_rate") BigDecimal exchangeRate
) {

}
