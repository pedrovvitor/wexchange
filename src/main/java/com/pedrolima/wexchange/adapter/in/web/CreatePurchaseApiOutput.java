package com.pedrolima.wexchange.adapter.in.web;

import com.pedrolima.wexchange.domain.purchase.Purchase;

import java.math.BigDecimal;
import java.util.List;

public record CreatePurchaseApiOutput(
        String id,
        String description,
        String date,
        BigDecimal amount,
        List<ApiLink> links
) {

    public static CreatePurchaseApiOutput with(final Purchase purchase, final List<ApiLink> links) {
        return new CreatePurchaseApiOutput(
                purchase.id(),
                purchase.description(),
                purchase.purchaseDate().toString(),
                purchase.amount().amount(),
                links);
    }
}
