package com.pedrolima.wexchange.api.purchase;

import java.math.BigDecimal;

public record ConvertPurchaseApiOutput(
        String id,
        String description,
        String transactionDate,
        String conversionCountryCurrency,
        BigDecimal originalAmount,
        BigDecimal rateValue,
        String exchangeRateEffectiveDate,
        BigDecimal convertedAmount
) {

    public static ConvertPurchaseApiOutput with(
            final String anId,
            final String aDescription,
            final String aTransactionDate,
            final BigDecimal anOriginalAmount,
            final String aCountryCurrency,
            final BigDecimal aRateValue,
            final String anExchangeRateEffectiveDate,
            final BigDecimal aConvertedAmount) {
        return new ConvertPurchaseApiOutput(
                anId,
                aDescription,
                aTransactionDate,
                aCountryCurrency,
                anOriginalAmount,
                aRateValue,
                anExchangeRateEffectiveDate,
                aConvertedAmount
        );
    }
}
