package com.pedrolima.wexchange.application;

import com.pedrolima.wexchange.application.port.ExchangeRateRefresher;
import com.pedrolima.wexchange.application.port.IdentifierGenerator;
import com.pedrolima.wexchange.application.port.PurchaseStore;
import com.pedrolima.wexchange.domain.money.Money;
import com.pedrolima.wexchange.domain.purchase.Purchase;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Records a purchase, then warms the rate cache for its conversion window if
 * this is the first purchase on that date.
 *
 * <p>The refresh is skipped for later purchases on a date already covered,
 * because the window is derived from the date alone: a second purchase on the
 * same day would fetch exactly the same rates.
 */
public class CreatePurchaseService implements CreatePurchaseUseCase {

    private final PurchaseStore purchases;

    private final ExchangeRateRefresher rateRefresher;

    private final IdentifierGenerator identifiers;

    private final Clock clock;

    public CreatePurchaseService(
            final PurchaseStore purchases,
            final ExchangeRateRefresher rateRefresher,
            final IdentifierGenerator identifiers,
            final Clock clock
    ) {
        this.purchases = purchases;
        this.rateRefresher = rateRefresher;
        this.identifiers = identifiers;
        this.clock = clock;
    }

    @Override
    public Purchase execute(final String description, final LocalDate purchaseDate, final BigDecimal amount) {
        final var purchase = Purchase.create(
                identifiers.newIdentifier(), description, purchaseDate, new Money(amount), clock.instant());

        final var stored = purchases.save(purchase);

        if (purchases.countByPurchaseDate(stored.purchaseDate()) <= 1) {
            rateRefresher.refreshFor(stored);
        }

        return stored;
    }
}
