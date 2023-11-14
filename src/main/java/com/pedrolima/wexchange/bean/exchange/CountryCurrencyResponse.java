package com.pedrolima.wexchange.bean.exchange;

import java.util.List;

public record CountryCurrencyResponse(
        List<CountryCurrencyData> data
) {

}
