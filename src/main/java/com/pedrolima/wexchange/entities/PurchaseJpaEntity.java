package com.pedrolima.wexchange.entities;

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
import java.util.UUID;

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

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant updatedAt;

    private PurchaseJpaEntity(final String description, final LocalDate purchaseDate, final BigDecimal amount) {
        final var now = Instant.now();
        this.id = UUID.randomUUID().toString();
        this.description = description;
        this.purchaseDate = purchaseDate;
        this.amount = amount.setScale(2, RoundingMode.HALF_EVEN);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public PurchaseJpaEntity() {

    }

    public static PurchaseJpaEntity newPurchase(final String description, final LocalDate date, final BigDecimal amount) {
        return new PurchaseJpaEntity(description, date, amount);
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
