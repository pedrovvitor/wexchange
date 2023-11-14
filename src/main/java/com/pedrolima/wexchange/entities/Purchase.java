package com.pedrolima.wexchange.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "description", nullable = false, length = 50)
    private String description;

    @Column(name = "date", nullable = false)
    private LocalDate date;
    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant updatedAt;

    public Purchase(final String description, final LocalDate date, final BigDecimal amount) {
        final var now = Instant.now();
        this.id = UUID.randomUUID().toString();
        this.description = description;
        this.date = date;
        this.amount = amount;
        this.createdAt = now;
        this.updatedAt = now;
    }
    public Purchase() {

    }
    public static Purchase newPurchase(final String description, final LocalDate date, final BigDecimal amount) {
        return new Purchase(description, date, amount);
    }

    public String getId() {
        return id;
    }
    public String getDescription() {
        return description;
    }
    public LocalDate getDate() {
        return date;
    }
    public BigDecimal getAmount() {
        return amount;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Purchase purchase = (Purchase) o;
        return Objects.equals(id, purchase.id);
    }
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
