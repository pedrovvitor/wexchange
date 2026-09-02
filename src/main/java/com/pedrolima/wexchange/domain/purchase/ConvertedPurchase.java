package com.pedrolima.wexchange.domain.purchase;

import com.pedrolima.wexchange.domain.exchange.ExchangeRate;
import com.pedrolima.wexchange.domain.money.Money;

/**
 * A purchase expressed in another currency, together with the rate that produced
 * the figure. The rate travels with the result because a converted amount
 * without its rate and effective date cannot be audited.
 */
public record ConvertedPurchase(Purchase purchase, ExchangeRate rate, Money convertedAmount) {
}
