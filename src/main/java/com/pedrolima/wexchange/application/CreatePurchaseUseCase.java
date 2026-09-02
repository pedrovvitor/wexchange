package com.pedrolima.wexchange.application;

import com.pedrolima.wexchange.domain.purchase.Purchase;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Records a purchase and returns it as stored. */
public interface CreatePurchaseUseCase {

    Purchase execute(String description, LocalDate purchaseDate, BigDecimal amount);
}
