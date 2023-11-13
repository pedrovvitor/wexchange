package com.pedrolima.wexchange.bean.exchange;

import java.util.List;

public record ExchangeRateResponse(
        List<ExchangeRateData> data
) {

}
