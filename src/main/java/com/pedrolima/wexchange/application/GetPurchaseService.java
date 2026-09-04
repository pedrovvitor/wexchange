package com.pedrolima.wexchange.application;

import com.pedrolima.wexchange.application.port.PurchaseStore;
import com.pedrolima.wexchange.domain.error.ResourceNotFoundException;
import com.pedrolima.wexchange.domain.purchase.Purchase;

public class GetPurchaseService implements GetPurchaseUseCase {

    private final PurchaseStore purchases;

    public GetPurchaseService(final PurchaseStore purchases) {
        this.purchases = purchases;
    }

    @Override
    public Purchase execute(final String purchaseId) {
        return purchases.findById(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found for id: " + purchaseId));
    }
}
