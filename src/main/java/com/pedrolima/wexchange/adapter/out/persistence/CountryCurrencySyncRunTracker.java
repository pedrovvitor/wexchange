package com.pedrolima.wexchange.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Records the country-currency scheduled sync's run status (issue #6),
 * independently of the job's own transaction: every method here runs in a
 * fresh {@code REQUIRES_NEW} transaction so a failure record actually commits
 * even though the job's own transaction - which holds the advisory lock the
 * job is running under - is about to roll back because of that very failure.
 *
 * <p>Safe to read-then-write without extra locking: the advisory lock this
 * tracker's caller already holds for the whole run serializes every write
 * here across replicas, since only one replica's job can be inside its
 * locked section at a time.
 */
@Component
public class CountryCurrencySyncRunTracker {

    private final CountryCurrencySyncRunRepository repository;

    public CountryCurrencySyncRunTracker(final CountryCurrencySyncRunRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRunning(final Instant startedAt) {
        final var previous = repository.findById(CountryCurrencySyncRunJpaEntity.SINGLETON_ID);
        repository.save(CountryCurrencySyncRunJpaEntity.running(startedAt, previous));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(final Instant startedAt, final Instant finishedAt) {
        final var previous = repository.findById(CountryCurrencySyncRunJpaEntity.SINGLETON_ID);
        repository.save(CountryCurrencySyncRunJpaEntity.succeeded(startedAt, finishedAt, previous));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(final Instant startedAt, final Instant finishedAt, final String errorMessage) {
        final var previous = repository.findById(CountryCurrencySyncRunJpaEntity.SINGLETON_ID);
        repository.save(CountryCurrencySyncRunJpaEntity.failed(startedAt, finishedAt, errorMessage, previous));
    }
}
