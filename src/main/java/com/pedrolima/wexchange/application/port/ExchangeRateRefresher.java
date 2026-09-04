package com.pedrolima.wexchange.application.port;

import com.pedrolima.wexchange.domain.purchase.Purchase;

/**
 * Asks the rate provider to refresh what is stored for a purchase's conversion
 * window.
 *
 * <p>Deliberately fire-and-forget and free of any result: the adapter behind it
 * runs asynchronously with its own retry policy, so a caller could not act on a
 * return value anyway. Keeping that behaviour inside the adapter is why this
 * port has one method and nothing orchestrates above it.
 */
public interface ExchangeRateRefresher {

    void refreshFor(Purchase purchase);
}
