package com.pedrolima.wexchange.application;

import com.pedrolima.wexchange.domain.purchase.Purchase;

/** Retrieves a purchase by its identifier. */
public interface GetPurchaseUseCase {

    Purchase execute(String purchaseId);
}
