package com.pedrolima.wexchange.api.purchase;

public record CreatePurchaseApiOutput(
        String id
) {

    public static CreatePurchaseApiOutput with(String id) {
        return new CreatePurchaseApiOutput(id);
    }
}
