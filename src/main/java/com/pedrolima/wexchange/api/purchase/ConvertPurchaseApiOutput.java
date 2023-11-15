package com.pedrolima.wexchange.api.purchase;

import java.math.BigDecimal;

public record ConvertPurchaseApiOutput(
        String id,
        String description,
        String transactionDate,
        String conversionCountryCurrency,
        BigDecimal originalAmount,
        BigDecimal exchangeRate,
        String exchangeRateEffectiveDate,
        BigDecimal convertedAmount
) {

    public static ConvertPurchaseApiOutput with(
            String anId,
            String aDescription,
            String aTransactionDate,
            BigDecimal anOriginalAmount,
            String aCountryCurrency,
            BigDecimal aExchangeRate,
            String anExchangeRateEffectiveDate,
            BigDecimal aConvertedAmount) {
        return new ConvertPurchaseApiOutput(
                anId,
                aDescription,
                aTransactionDate,
                aCountryCurrency,
                anOriginalAmount,
                aExchangeRate,
                anExchangeRateEffectiveDate,
                aConvertedAmount
        );
    }
}
