package com.pedrolima.wexchange.adapter.out.persistence;

import com.pedrolima.wexchange.domain.money.Money;
import com.pedrolima.wexchange.domain.purchase.Purchase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "purchase")
@Getter
public class PurchaseJpaEntity {

    @Id
    private String id;

    @Column(name = "description", nullable = false, length = 50)
    private String description;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private PurchaseJpaEntity(
            final String id,
            final String description,
            final LocalDate purchaseDate,
            final BigDecimal amount,
            final Instant createdAt,
            final Instant updatedAt
    ) {
        this.id = id;
        this.description = description;
        this.purchaseDate = purchaseDate;
        this.amount = amount.setScale(2, RoundingMode.HALF_EVEN);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public PurchaseJpaEntity() {

    }

    /**
     * Creates a purchase from values the caller has already decided.
     *
     * <p>The identifier and the timestamp are parameters rather than being read
     * from {@code UUID.randomUUID()} and {@code Instant.now()} here. The entity
     * is then a pure function of its arguments, and the use case that owns the
     * decision supplies them from an injected {@code IdentifierGenerator} and
     * {@code Clock}.
     */
    /** Copies a domain purchase into its persistence shape. */
    public static PurchaseJpaEntity fromDomain(final Purchase purchase) {
        return new PurchaseJpaEntity(
                purchase.id(),
                purchase.description(),
                purchase.purchaseDate(),
                purchase.amount().amount(),
                purchase.createdAt(),
                purchase.updatedAt());
    }

    /** Rebuilds the domain purchase this row represents. */
    public Purchase toDomain() {
        return Purchase.restore(id, description, purchaseDate, new Money(amount), createdAt, updatedAt);
    }

    public static PurchaseJpaEntity newPurchase(
            final String anId,
            final String aDescription,
            final LocalDate aPurchaseDate,
            final BigDecimal anAmount,
            final Instant now
    ) {
        return new PurchaseJpaEntity(anId, aDescription, aPurchaseDate, anAmount, now, now);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final PurchaseJpaEntity purchase = (PurchaseJpaEntity) o;
        return Objects.equals(id, purchase.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
