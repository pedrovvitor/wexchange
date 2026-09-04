package com.pedrolima.wexchange.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CountryCurrencySyncRunRepository extends JpaRepository<CountryCurrencySyncRunJpaEntity, String> {

    /**
     * Attempts a PostgreSQL transaction-scoped advisory lock (issue #6): held
     * for the duration of the caller's current transaction and released
     * automatically when it commits, rolls back, or the holding connection is
     * lost - including a replica crashing mid-run, with no separate staleness
     * check needed. Returns {@code false} without blocking if another
     * connection (another replica, or a concurrent run on this one) already
     * holds it.
     */
    @Query(value = "SELECT pg_try_advisory_xact_lock(:key)", nativeQuery = true)
    boolean tryAcquireSyncLock(@Param("key") long key);
}
