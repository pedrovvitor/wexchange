package com.pedrolima.wexchange.application;

import com.pedrolima.wexchange.domain.purchase.Purchase;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Records a purchase and returns it as stored.
 *
 * <p>{@code idempotencyKey}, when non-null, makes the call safely retryable
 * (issue #18): repeating it with the same key and the same description, date,
 * and amount replays the same {@link Purchase} instead of creating a second
 * one; repeating it with a different payload is a conflict. A {@code null}
 * key opts out entirely - the call always creates a new purchase, exactly as
 * before this behavior existed.
 */
public interface CreatePurchaseUseCase {

    Purchase execute(String description, LocalDate purchaseDate, BigDecimal amount, String idempotencyKey);
}
