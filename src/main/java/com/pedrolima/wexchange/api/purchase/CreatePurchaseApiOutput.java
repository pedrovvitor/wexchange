package com.pedrolima.wexchange.api.purchase;

import com.pedrolima.wexchange.api.ApiLink;
import com.pedrolima.wexchange.entities.PurchaseJpaEntity;

import java.math.BigDecimal;
import java.util.List;

public record CreatePurchaseApiOutput(
        String id,
        String description,
        String date,
        BigDecimal amount,
        List<ApiLink> links
) {

    public static CreatePurchaseApiOutput with(final PurchaseJpaEntity aPurchase, final List<ApiLink> links) {
        return new CreatePurchaseApiOutput(
                aPurchase.getId(),
                aPurchase.getDescription(),
                aPurchase.getDate().toString(),
                aPurchase.getAmount(),
                links);
    }
}
