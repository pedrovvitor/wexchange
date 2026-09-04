package com.pedrolima.wexchange.adapter.in.web;

import com.pedrolima.wexchange.domain.purchase.Purchase;

import java.math.BigDecimal;
import java.util.List;

/** The representation of a purchase, shared by creation and retrieval. */
public record PurchaseApiOutput(
        String id,
        String description,
        String date,
        BigDecimal amount,
        List<ApiLink> links
) {

    public static PurchaseApiOutput with(final Purchase purchase, final List<ApiLink> links) {
        return new PurchaseApiOutput(
                purchase.id(),
                purchase.description(),
                purchase.purchaseDate().toString(),
                purchase.amount().amount(),
                links);
    }
}
