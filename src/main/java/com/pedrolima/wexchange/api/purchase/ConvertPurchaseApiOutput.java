package com.pedrolima.wexchange.api.purchase;

import com.pedrolima.wexchange.api.ApiLink;

import java.math.BigDecimal;
import java.util.List;

public record ConvertPurchaseApiOutput(
        String id,
        String description,
        String transactionDate,
        String conversionCountryCurrency,
        BigDecimal originalAmount,
        BigDecimal rateValue,
        String rateEffectiveDate,
        BigDecimal convertedAmount,
        List<ApiLink> links
) {

    public static ConvertPurchaseApiOutput with(
            final String anId,
            final String aDescription,
            final String aTransactionDate,
            final BigDecimal anOriginalAmount,
            final String aCountryCurrency,
            final BigDecimal aRateValue,
            final String aRateEffectiveDate,
            final BigDecimal aConvertedAmount,
            final List<ApiLink> links) {
        return new ConvertPurchaseApiOutput(
                anId,
                aDescription,
                aTransactionDate,
                aCountryCurrency,
                anOriginalAmount,
                aRateValue,
                aRateEffectiveDate,
                aConvertedAmount,
                links
        );
    }
}
