package com.pedrolima.wexchange.adapter.in.web;

public record ConvertPurchaseApiInput(
        String purchaseId,
        String countryCurrency
) {

    public static ConvertPurchaseApiInput with(final String anId, final String aCountryCurrency) {
        return new ConvertPurchaseApiInput(anId, aCountryCurrency);
    }
}
