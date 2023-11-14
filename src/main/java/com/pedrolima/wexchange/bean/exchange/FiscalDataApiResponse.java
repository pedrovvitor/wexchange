package com.pedrolima.wexchange.bean.exchange;

import java.util.List;

public record FiscalDataApiResponse(
        List<ExchangeData> data
) {

}
