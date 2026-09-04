package com.pedrolima.wexchange.adapter.out.persistence;

import com.pedrolima.wexchange.application.port.IdempotencyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyJpaEntity, String> {

    @Modifying
    @Query("UPDATE IdempotencyKeyJpaEntity e SET e.status = com.pedrolima.wexchange.application.port.IdempotencyStatus.COMPLETED, "
            + "e.resourceId = :resourceId, e.updatedAt = :now WHERE e.idempotencyKey = :idempotencyKey")
    int completeClaim(
            @Param("idempotencyKey") String idempotencyKey,
            @Param("resourceId") String resourceId,
            @Param("now") Instant now);

    @Modifying
    @Query("UPDATE IdempotencyKeyJpaEntity e SET e.status = com.pedrolima.wexchange.application.port.IdempotencyStatus.FAILED, "
            + "e.updatedAt = :now WHERE e.idempotencyKey = :idempotencyKey")
    int failClaim(@Param("idempotencyKey") String idempotencyKey, @Param("now") Instant now);

    /**
     * Atomically resurrects a record stuck at {@link IdempotencyStatus#FAILED}
     * or past its {@code expiresAt} back to {@link IdempotencyStatus#IN_PROGRESS}.
     * The {@code WHERE} clause is the actual concurrency defense: of several
     * concurrent callers running this same statement, the database guarantees
     * only one {@code UPDATE} matches a row still in a reclaimable state, so
     * only one caller ever sees a row-count of 1.
     */
    @Modifying
    @Query("UPDATE IdempotencyKeyJpaEntity e SET e.status = com.pedrolima.wexchange.application.port.IdempotencyStatus.IN_PROGRESS, "
            + "e.updatedAt = :now WHERE e.idempotencyKey = :idempotencyKey "
            + "AND (e.status = com.pedrolima.wexchange.application.port.IdempotencyStatus.FAILED OR e.expiresAt < :now)")
    int reclaim(@Param("idempotencyKey") String idempotencyKey, @Param("now") Instant now);
}
