package com.pedrolima.wexchange.adapter.out.persistence;

import com.pedrolima.wexchange.application.port.IdempotencyRecord;
import com.pedrolima.wexchange.application.port.PurchaseIdempotencyStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.Instant;
import java.util.Optional;

/**
 * Backs {@link PurchaseIdempotencyStore} with Spring Data (issue #18).
 *
 * <p>{@link #claim} cannot use {@code repository.save(...)}: with an
 * application-assigned id and no version field, Spring Data treats the entity
 * as "not new" and issues a {@code merge} rather than an {@code insert}
 * ({@link PurchaseStoreAdapter} has the identical characteristic, documented
 * on {@link PurchaseJpaEntity}) - a second claim for the same key would
 * silently overwrite the first instead of ever reaching the unique-constraint
 * violation this method exists to detect. Going through {@link EntityManager#persist}
 * directly forces a real {@code INSERT}, and running in its own
 * {@code REQUIRES_NEW} transaction isolates the attempt: the caller learns
 * only whether it won the claim, and the persistence exception - which would
 * otherwise leak a framework type across the port boundary - never escapes
 * this class.
 */
@Repository
public class PurchaseIdempotencyStoreAdapter implements PurchaseIdempotencyStore {

    private final IdempotencyKeyRepository repository;

    private final EntityManager entityManager;

    public PurchaseIdempotencyStoreAdapter(final IdempotencyKeyRepository repository, final EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(final String idempotencyKey, final String fingerprint, final Instant now, final Instant expiresAt) {
        try {
            entityManager.persist(IdempotencyKeyJpaEntity.newClaim(idempotencyKey, fingerprint, now, expiresAt));
            entityManager.flush();
            return true;
        } catch (final PersistenceException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reclaim(final String idempotencyKey, final Instant now) {
        return repository.reclaim(idempotencyKey, now) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(final String idempotencyKey, final String resourceId, final Instant now) {
        repository.completeClaim(idempotencyKey, resourceId, now);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(final String idempotencyKey, final Instant now) {
        repository.failClaim(idempotencyKey, now);
    }

    @Override
    public Optional<IdempotencyRecord> find(final String idempotencyKey) {
        return repository.findById(idempotencyKey).map(entity -> new IdempotencyRecord(
                entity.getIdempotencyKey(),
                entity.getFingerprint(),
                entity.getStatus(),
                entity.getResourceId(),
                entity.getExpiresAt()));
    }
}
