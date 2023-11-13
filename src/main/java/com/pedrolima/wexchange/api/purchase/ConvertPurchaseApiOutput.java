package com.pedrolima.wexchange.api.purchase;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ConvertPurchaseApiOutput(
        String id,
        String description,
        LocalDate transactionDate,
        BigDecimal originalAmount,
        BigDecimal exchangeRate,
        BigDecimal convertedAmount
) {

}
