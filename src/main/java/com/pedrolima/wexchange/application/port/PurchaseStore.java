package com.pedrolima.wexchange.application.port;

import com.pedrolima.wexchange.domain.purchase.Purchase;

import java.time.LocalDate;
import java.util.Optional;

/** Storage for purchases. Implemented by the persistence adapter. */
public interface PurchaseStore {

    Purchase save(Purchase purchase);

    Optional<Purchase> findById(String id);

    long countByPurchaseDate(LocalDate purchaseDate);
}
