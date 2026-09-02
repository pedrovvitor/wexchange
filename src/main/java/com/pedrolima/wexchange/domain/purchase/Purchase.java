package com.pedrolima.wexchange.domain.purchase;

import com.pedrolima.wexchange.domain.exchange.ConversionWindow;
import com.pedrolima.wexchange.domain.exchange.ExchangeRate;
import com.pedrolima.wexchange.domain.money.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A recorded purchase in its original currency.
 *
 * <p>Identity and timestamps are supplied by the caller rather than read from
 * {@code UUID.randomUUID()} and {@code Instant.now()}, so a purchase is a pure
 * function of its arguments and the application layer owns those decisions.
 */
public record Purchase(
        String id,
        String description,
        LocalDate purchaseDate,
        Money amount,
        Instant createdAt,
        Instant updatedAt
) {

    /** Records a new purchase, enforcing the invariants over its fields. */
    public static Purchase create(
            final String id,
            final String description,
            final LocalDate purchaseDate,
            final Money amount,
            final Instant now
    ) {
        requireText(id, "id");
        requireText(description, "description");
        Objects.requireNonNull(purchaseDate, "purchaseDate must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (amount.isNegative()) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        return new Purchase(id, description, purchaseDate, amount, now, now);
    }

    /**
     * Rebuilds a purchase already held in storage.
     *
     * <p>Separate from {@link #create} deliberately: a persisted record must stay
     * loadable even if the invariants are later tightened, otherwise a validation
     * change makes old rows unreadable.
     */
    public static Purchase restore(
            final String id,
            final String description,
            final LocalDate purchaseDate,
            final Money amount,
            final Instant createdAt,
            final Instant updatedAt
    ) {
        return new Purchase(id, description, purchaseDate, amount, createdAt, updatedAt);
    }

    public ConversionWindow conversionWindow() {
        return ConversionWindow.endingOn(purchaseDate);
    }

    public ConvertedPurchase convertWith(final ExchangeRate rate) {
        Objects.requireNonNull(rate, "rate must not be null");
        return new ConvertedPurchase(this, rate, rate.convert(amount));
    }

    private static void requireText(final String value, final String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
