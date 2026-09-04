package com.pedrolima.wexchange.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link CountryCurrencySyncRunTracker} against a real PostgreSQL (issue #6):
 * proves the read-then-write it does to preserve the previous success/failure
 * timestamp actually round-trips through a real transaction boundary
 * ({@code REQUIRES_NEW} per call), not just that the right entity was passed
 * to a mocked {@code save}.
 */
@Import(CountryCurrencySyncRunTracker.class)
class CountryCurrencySyncRunTrackerIT extends AbstractPostgresRepositoryIT {

    @Autowired
    private CountryCurrencySyncRunTracker tracker;

    @Autowired
    private CountryCurrencySyncRunRepository repository;

    @Test
    void givenASuccessfulRun_whenRecorded_thenItIsObservable() {
        final var startedAt = Instant.parse("2024-01-01T02:00:00Z");
        final var finishedAt = Instant.parse("2024-01-01T02:00:05Z");

        tracker.recordRunning(startedAt);
        tracker.recordSuccess(startedAt, finishedAt);

        final var stored = repository.findById(CountryCurrencySyncRunJpaEntity.SINGLETON_ID).orElseThrow();
        assertEquals(SyncRunStatus.SUCCEEDED, stored.getStatus());
        assertEquals(finishedAt, stored.getLastSuccessAt());
    }

    @Test
    void givenAFailureAfterAPreviousSuccess_whenRecorded_thenTheLastSuccessIsStillObservable() {
        final var firstStartedAt = Instant.parse("2024-01-02T02:00:00Z");
        final var firstFinishedAt = Instant.parse("2024-01-02T02:00:05Z");
        tracker.recordRunning(firstStartedAt);
        tracker.recordSuccess(firstStartedAt, firstFinishedAt);

        final var secondStartedAt = Instant.parse("2024-01-03T02:00:00Z");
        final var secondFinishedAt = Instant.parse("2024-01-03T02:00:02Z");
        tracker.recordRunning(secondStartedAt);
        tracker.recordFailure(secondStartedAt, secondFinishedAt, "upstream unavailable");

        final var stored = repository.findById(CountryCurrencySyncRunJpaEntity.SINGLETON_ID).orElseThrow();
        assertEquals(SyncRunStatus.FAILED, stored.getStatus());
        assertEquals(firstFinishedAt, stored.getLastSuccessAt(), "a failure must not erase the previous success");
        assertEquals(secondFinishedAt, stored.getLastFailureAt());
        assertEquals("upstream unavailable", stored.getLastErrorMessage());
    }

    @Test
    void givenASuccessAfterAPreviousFailure_whenRecorded_thenTheLastFailureIsStillObservable() {
        final var firstStartedAt = Instant.parse("2024-01-04T02:00:00Z");
        final var firstFinishedAt = Instant.parse("2024-01-04T02:00:02Z");
        tracker.recordRunning(firstStartedAt);
        tracker.recordFailure(firstStartedAt, firstFinishedAt, "timeout");

        final var secondStartedAt = Instant.parse("2024-01-05T02:00:00Z");
        final var secondFinishedAt = Instant.parse("2024-01-05T02:00:05Z");
        tracker.recordRunning(secondStartedAt);
        tracker.recordSuccess(secondStartedAt, secondFinishedAt);

        final var stored = repository.findById(CountryCurrencySyncRunJpaEntity.SINGLETON_ID).orElseThrow();
        assertEquals(SyncRunStatus.SUCCEEDED, stored.getStatus());
        assertEquals(secondFinishedAt, stored.getLastSuccessAt());
        assertEquals(firstFinishedAt, stored.getLastFailureAt(), "a success must not erase the previous failure");
        assertNull(stored.getLastErrorMessage(), "a successful run's own row carries no error message");
    }
}
