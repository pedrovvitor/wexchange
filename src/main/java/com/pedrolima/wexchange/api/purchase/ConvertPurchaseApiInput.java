package com.pedrolima.wexchange.api.purchase;

public record ConvertPurchaseApiInput(
        String purchaseId,
        String countryCurrency
) {

}
