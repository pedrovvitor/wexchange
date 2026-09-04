package com.pedrolima.wexchange.application;

import com.pedrolima.wexchange.application.port.ExchangeRateRefresher;
import com.pedrolima.wexchange.application.port.IdempotencyRecord;
import com.pedrolima.wexchange.application.port.IdentifierGenerator;
import com.pedrolima.wexchange.application.port.PurchaseIdempotencyStore;
import com.pedrolima.wexchange.application.port.PurchaseStore;
import com.pedrolima.wexchange.domain.error.IdempotencyKeyConflictException;
import com.pedrolima.wexchange.domain.error.ResourceNotFoundException;
import com.pedrolima.wexchange.domain.error.RetryableException;
import com.pedrolima.wexchange.domain.money.Money;
import com.pedrolima.wexchange.domain.purchase.Purchase;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Records a purchase, then warms the rate cache for its conversion window if
 * this is the first purchase on that date.
 *
 * <p>The refresh is skipped for later purchases on a date already covered,
 * because the window is derived from the date alone: a second purchase on the
 * same day would fetch exactly the same rates.
 *
 * <p>When an {@code idempotencyKey} is supplied (issue #18), a repeated call
 * with the same key and the same description/date/amount replays the same
 * {@link Purchase} instead of creating a second one, and never re-triggers
 * the refresh above - it only ever runs once per key, on the request that
 * actually creates the purchase. The store's own database unique constraint
 * on the key is the final concurrency defense: {@link #execute} can lose a
 * claim race to another replica, and that is by design, not a bug this class
 * works around - the loser simply polls for the winner's outcome instead.
 */
public class CreatePurchaseService implements CreatePurchaseUseCase {

    private final PurchaseStore purchases;

    private final ExchangeRateRefresher rateRefresher;

    private final IdentifierGenerator identifiers;

    private final Clock clock;

    private final PurchaseIdempotencyStore idempotencyStore;

    private final Duration retention;

    private final Duration maxWait;

    private final Duration pollInterval;

    public CreatePurchaseService(
            final PurchaseStore purchases,
            final ExchangeRateRefresher rateRefresher,
            final IdentifierGenerator identifiers,
            final Clock clock,
            final PurchaseIdempotencyStore idempotencyStore,
            final Duration retention,
            final Duration maxWait,
            final Duration pollInterval
    ) {
        this.purchases = purchases;
        this.rateRefresher = rateRefresher;
        this.identifiers = identifiers;
        this.clock = clock;
        this.idempotencyStore = idempotencyStore;
        this.retention = retention;
        this.maxWait = maxWait;
        this.pollInterval = pollInterval;
    }

    @Override
    public Purchase execute(
            final String description,
            final LocalDate purchaseDate,
            final BigDecimal amount,
            final String idempotencyKey
    ) {
        if (idempotencyKey == null) {
            return createNew(description, purchaseDate, amount);
        }

        final String fingerprint = fingerprint(description, purchaseDate, amount);
        final Instant now = clock.instant();

        if (idempotencyStore.claim(idempotencyKey, fingerprint, now, now.plus(retention))) {
            return createAsOwner(idempotencyKey, description, purchaseDate, amount);
        }

        return joinExistingClaim(idempotencyKey, fingerprint, description, purchaseDate, amount);
    }

    private Purchase createNew(final String description, final LocalDate purchaseDate, final BigDecimal amount) {
        final var purchase = Purchase.create(
                identifiers.newIdentifier(), description, purchaseDate, new Money(amount), clock.instant());

        final var stored = purchases.save(purchase);

        if (purchases.countByPurchaseDate(stored.purchaseDate()) <= 1) {
            rateRefresher.refreshFor(stored);
        }

        return stored;
    }

    private Purchase createAsOwner(
            final String idempotencyKey,
            final String description,
            final LocalDate purchaseDate,
            final BigDecimal amount
    ) {
        final Purchase created;
        try {
            created = createNew(description, purchaseDate, amount);
        } catch (final RuntimeException e) {
            idempotencyStore.markFailed(idempotencyKey, clock.instant());
            throw e;
        }
        idempotencyStore.markCompleted(idempotencyKey, created.id(), clock.instant());
        return created;
    }

    /**
     * Reached only when {@link #execute}'s own claim lost a race: a record for
     * this key already exists, owned by this call or an earlier one. Polls,
     * bounded by {@link #maxWait}, until {@link #tryResolve} finds that record
     * resolved - completed (replay), failed or expired (this call reclaims and
     * becomes the new owner), or a fingerprint mismatch (conflict, which
     * {@link #tryResolve} throws rather than returns).
     */
    private Purchase joinExistingClaim(
            final String idempotencyKey,
            final String fingerprint,
            final String description,
            final LocalDate purchaseDate,
            final BigDecimal amount
    ) {
        final Instant deadline = clock.instant().plus(maxWait);

        while (true) {
            final Optional<Purchase> resolved = tryResolve(idempotencyKey, fingerprint, description, purchaseDate, amount);
            if (resolved.isPresent()) {
                return resolved.get();
            }

            if (!clock.instant().isBefore(deadline)) {
                throw new RetryableException(
                        "Idempotency-Key " + idempotencyKey + " is still being processed by another request");
            }
            sleep(pollInterval);
        }
    }

    /** One polling attempt. Empty means: still unresolved, keep polling. */
    private Optional<Purchase> tryResolve(
            final String idempotencyKey,
            final String fingerprint,
            final String description,
            final LocalDate purchaseDate,
            final BigDecimal amount
    ) {
        final Optional<IdempotencyRecord> found = idempotencyStore.find(idempotencyKey);
        if (found.isEmpty()) {
            return claimAsOwner(idempotencyKey, fingerprint, description, purchaseDate, amount);
        }

        final IdempotencyRecord record = found.get();
        if (!record.fingerprint().equals(fingerprint)) {
            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key " + idempotencyKey + " was already used with a different request");
        }

        return switch (record.status()) {
            case COMPLETED -> Optional.of(replay(record));
            case FAILED -> reclaimAsOwner(idempotencyKey, description, purchaseDate, amount);
            case IN_PROGRESS -> record.expiresAt().isBefore(clock.instant())
                    ? reclaimAsOwner(idempotencyKey, description, purchaseDate, amount)
                    : Optional.empty();
        };
    }

    private Optional<Purchase> claimAsOwner(
            final String idempotencyKey,
            final String fingerprint,
            final String description,
            final LocalDate purchaseDate,
            final BigDecimal amount
    ) {
        final Instant now = clock.instant();
        if (idempotencyStore.claim(idempotencyKey, fingerprint, now, now.plus(retention))) {
            return Optional.of(createAsOwner(idempotencyKey, description, purchaseDate, amount));
        }
        return Optional.empty();
    }

    private Optional<Purchase> reclaimAsOwner(
            final String idempotencyKey,
            final String description,
            final LocalDate purchaseDate,
            final BigDecimal amount
    ) {
        if (idempotencyStore.reclaim(idempotencyKey, clock.instant())) {
            return Optional.of(createAsOwner(idempotencyKey, description, purchaseDate, amount));
        }
        return Optional.empty();
    }

    private Purchase replay(final IdempotencyRecord record) {
        return purchases.findById(record.resourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found for id: " + record.resourceId()));
    }

    private static void sleep(final Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RetryableException("Interrupted while waiting for an in-progress idempotent request", e);
        }
    }

    private static String fingerprint(final String description, final LocalDate purchaseDate, final BigDecimal amount) {
        final String canonicalPayload = description.length() + ":" + description
                + "|" + purchaseDate
                + "|" + new Money(amount);
        try {
            final var digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
