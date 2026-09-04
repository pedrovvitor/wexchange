package com.pedrolima.wexchange.adapter.out.persistence;

import com.pedrolima.wexchange.application.port.IdempotencyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "idempotency_key")
@Getter
public class IdempotencyKeyJpaEntity {

    @Id
    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IdempotencyStatus status;

    @Column(name = "resource_id", length = 36)
    private String resourceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyKeyJpaEntity() {

    }

    private IdempotencyKeyJpaEntity(
            final String idempotencyKey,
            final String fingerprint,
            final IdempotencyStatus status,
            final String resourceId,
            final Instant createdAt,
            final Instant updatedAt,
            final Instant expiresAt
    ) {
        this.idempotencyKey = idempotencyKey;
        this.fingerprint = fingerprint;
        this.status = status;
        this.resourceId = resourceId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.expiresAt = expiresAt;
    }

    public static IdempotencyKeyJpaEntity newClaim(
            final String idempotencyKey,
            final String fingerprint,
            final Instant now,
            final Instant expiresAt
    ) {
        return new IdempotencyKeyJpaEntity(
                idempotencyKey, fingerprint, IdempotencyStatus.IN_PROGRESS, null, now, now, expiresAt);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final IdempotencyKeyJpaEntity that = (IdempotencyKeyJpaEntity) o;
        return Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idempotencyKey);
    }
}
