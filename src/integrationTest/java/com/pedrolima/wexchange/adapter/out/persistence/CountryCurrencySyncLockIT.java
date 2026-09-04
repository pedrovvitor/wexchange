package com.pedrolima.wexchange.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code pg_try_advisory_xact_lock} against a real PostgreSQL (issue #6):
 * only a genuine second connection, holding its own real transaction, proves
 * two replicas actually serialize against each other - a mock repository
 * would just record two calls and tell you nothing about the database's own
 * mutual-exclusion guarantee.
 *
 * <p>{@code NOT_SUPPORTED} at the class level turns off {@code @DataJpaTest}'s
 * own default test transaction: this class needs each {@link TransactionTemplate}
 * call below to be a genuinely separate transaction (and, in the concurrent
 * test, a genuinely separate connection on a different thread) rather than
 * nesting inside one ambient test transaction that would make both "replicas"
 * the same transaction in disguise.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CountryCurrencySyncLockIT extends AbstractPostgresRepositoryIT {

    @Autowired
    private CountryCurrencySyncRunRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void givenALockHeldByOneTransaction_whenAnotherTriesToAcquireIt_thenItFails() throws InterruptedException {
        final long key = 918_273_645L;
        final var holderAcquired = new CountDownLatch(1);
        final var releaseHolder = new CountDownLatch(1);
        final var holderDone = new CountDownLatch(1);
        final var secondAttemptResult = new AtomicBoolean(true);

        final Thread holder = new Thread(() -> {
            final var tx = new TransactionTemplate(transactionManager);
            tx.executeWithoutResult(status -> {
                repository.tryAcquireSyncLock(key);
                holderAcquired.countDown();
                try {
                    releaseHolder.await(5, TimeUnit.SECONDS);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            holderDone.countDown();
        });
        holder.start();

        assertTrue(holderAcquired.await(5, TimeUnit.SECONDS), "the first transaction never acquired the lock");

        final var tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> secondAttemptResult.set(repository.tryAcquireSyncLock(key)));

        assertFalse(secondAttemptResult.get(), "a second transaction must not acquire a lock the first still holds");

        releaseHolder.countDown();
        assertTrue(holderDone.await(5, TimeUnit.SECONDS));
    }

    @Test
    void givenALockWasReleasedByCommit_whenAcquiringAgain_thenItSucceeds() {
        final long key = 192_837_465L;
        final var tx = new TransactionTemplate(transactionManager);

        final boolean firstAttempt = Boolean.TRUE.equals(tx.execute(status -> repository.tryAcquireSyncLock(key)));
        final boolean secondAttempt = Boolean.TRUE.equals(tx.execute(status -> repository.tryAcquireSyncLock(key)));

        assertTrue(firstAttempt, "the first, uncontended attempt must acquire the lock");
        assertTrue(secondAttempt, "a lock released by the previous transaction's commit must be acquirable again");
    }
}
