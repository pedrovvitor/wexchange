package com.pedrolima.wexchange.api.purchase;

import java.math.BigDecimal;

public record ConvertPurchaseApiOutput(
        String id,
        String description,
        String transactionDate,
        BigDecimal originalAmount,
        BigDecimal exchangeRate,
        BigDecimal convertedAmount
) {

}
