package com.pedrolima.wexchange.api.purchase;

import java.math.BigDecimal;

public record ConvertPurchaseApiOutput(
        String id,
        String description,
        String transactionDate,
        BigDecimal originalAmount,
        String country,
        String currency,
        BigDecimal exchangeRate,
        String exchangeRateEffectiveDate,
        BigDecimal convertedAmount
) {
    public static ConvertPurchaseApiOutput with(
            String anId,
            String aDescription,
            String aTransactionDate,
            BigDecimal anOriginalAmount,
            String aCountry,
            String aCurrency,
            BigDecimal aExchangeRate,
            String anExchangeRateEffectiveDate,
            BigDecimal aConvertedAmount) {
        return new ConvertPurchaseApiOutput(
                anId,
                aDescription,
                aTransactionDate,
                anOriginalAmount,
                aCountry,
                aCurrency,
                aExchangeRate,
                anExchangeRateEffectiveDate,
                aConvertedAmount
        );
    }
}
