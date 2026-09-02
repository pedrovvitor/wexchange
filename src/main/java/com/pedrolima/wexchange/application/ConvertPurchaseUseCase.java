package com.pedrolima.wexchange.application;

import com.pedrolima.wexchange.domain.purchase.ConvertedPurchase;

/** Expresses a stored purchase in another country-currency. */
public interface ConvertPurchaseUseCase {

    ConvertedPurchase execute(String purchaseId, String countryCurrency);
}
