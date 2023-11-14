package com.pedrolima.wexchange.api.purchase;

import com.pedrolima.wexchange.api.ApiLink;

import java.util.List;

public record CreatePurchaseApiOutput(
        String id,
        List<ApiLink> links
) {

    public static CreatePurchaseApiOutput with(String anId, List<ApiLink> links) {
        return new CreatePurchaseApiOutput(anId, links);
    }
}
