package com.pedrolima.wexchange.adapter.out.persistence;

import com.pedrolima.wexchange.application.port.IdempotencyStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PurchaseIdempotencyStoreAdapter} against a real PostgreSQL (issue
 * #18): the one thing worth a real database here is that {@link #claim}'s
 * unique-constraint defense actually holds under real concurrent writers, not
 * just under a single-threaded call.
 *
 * <p>{@code @Import} brings this concrete {@code @Repository} class into
 * {@code @DataJpaTest}'s otherwise JPA-repository-and-entity-only slice
 * (mirroring how {@code PersistenceConstraintsIT} only ever autowires
 * Spring Data repository interfaces, never a hand-written adapter class) -
 * without it, autowiring {@link PurchaseIdempotencyStoreAdapter} directly
 * would fail, and constructing it with {@code new} instead would silently
 * skip its {@code @Transactional} behavior, since that only applies to a
 * proxy Spring creates for a bean it manages.
 */
@Import(PurchaseIdempotencyStoreAdapter.class)
class PurchaseIdempotencyStoreAdapterIT extends AbstractPostgresRepositoryIT {

    @Autowired
    private PurchaseIdempotencyStoreAdapter adapter;

    @Test
    void givenANewKey_whenClaiming_thenItSucceedsAndTheRecordIsFound() {
        final Instant now = Instant.parse("2024-01-01T00:00:00Z");

        final boolean claimed = adapter.claim("key-1", "fingerprint-1", now, now.plusSeconds(3600));

        assertTrue(claimed);
        final var record = adapter.find("key-1").orElseThrow();
        assertEquals("fingerprint-1", record.fingerprint());
        assertEquals(IdempotencyStatus.IN_PROGRESS, record.status());
    }

    @Test
    void givenAnAlreadyClaimedKey_whenClaimingAgain_thenItFails() {
        final Instant now = Instant.parse("2024-01-01T00:00:00Z");
        adapter.claim("key-2", "fingerprint-a", now, now.plusSeconds(3600));

        final boolean claimedAgain = adapter.claim("key-2", "fingerprint-b", now, now.plusSeconds(3600));

        assertFalse(claimedAgain);
        // The first claim's own row must survive a failed second attempt untouched.
        assertEquals("fingerprint-a", adapter.find("key-2").orElseThrow().fingerprint());
    }

    @Test
    void givenACompletedClaim_whenMarkingCompleted_thenTheStatusAndResourceIdAreStored() {
        final Instant now = Instant.parse("2024-01-01T00:00:00Z");
        adapter.claim("key-3", "fingerprint", now, now.plusSeconds(3600));

        adapter.markCompleted("key-3", "purchase-123", now.plusSeconds(1));

        final var record = adapter.find("key-3").orElseThrow();
        assertEquals(IdempotencyStatus.COMPLETED, record.status());
        assertEquals("purchase-123", record.resourceId());
    }

    @Test
    void givenAClaim_whenMarkingFailed_thenTheStatusBecomesFailed() {
        final Instant now = Instant.parse("2024-01-01T00:00:00Z");
        adapter.claim("key-4", "fingerprint", now, now.plusSeconds(3600));

        adapter.markFailed("key-4", now.plusSeconds(1));

        assertEquals(IdempotencyStatus.FAILED, adapter.find("key-4").orElseThrow().status());
    }

    @Test
    void givenAFailedRecord_whenReclaiming_thenItSucceedsAndTheStatusReturnsToInProgress() {
        final Instant now = Instant.parse("2024-01-01T00:00:00Z");
        adapter.claim("key-5", "fingerprint", now, now.plusSeconds(3600));
        adapter.markFailed("key-5", now.plusSeconds(1));

        final boolean reclaimed = adapter.reclaim("key-5", now.plusSeconds(2));

        assertTrue(reclaimed);
        assertEquals(IdempotencyStatus.IN_PROGRESS, adapter.find("key-5").orElseThrow().status());
    }

    @Test
    void givenAnInProgressRecordStillWithinItsExpiry_whenReclaiming_thenItFails() {
        final Instant now = Instant.parse("2024-01-01T00:00:00Z");
        adapter.claim("key-6", "fingerprint", now, now.plusSeconds(3600));

        final boolean reclaimed = adapter.reclaim("key-6", now.plusSeconds(1));

        assertFalse(reclaimed);
        assertEquals(IdempotencyStatus.IN_PROGRESS, adapter.find("key-6").orElseThrow().status());
    }

    @Test
    void givenAnExpiredInProgressRecord_whenReclaiming_thenItSucceeds() {
        final Instant now = Instant.parse("2024-01-01T00:00:00Z");
        adapter.claim("key-7", "fingerprint", now, now.plusSeconds(60));

        final boolean reclaimed = adapter.reclaim("key-7", now.plusSeconds(120));

        assertTrue(reclaimed);
        assertEquals(IdempotencyStatus.IN_PROGRESS, adapter.find("key-7").orElseThrow().status());
    }

    @Test
    void givenNoStoredKey_whenFinding_thenItIsEmpty() {
        assertTrue(adapter.find("never-stored").isEmpty());
    }

    @Test
    void givenManyConcurrentClaimsForTheSameKey_whenClaiming_thenOnlyOneSucceeds() throws InterruptedException {
        final int callers = 20;
        final ExecutorService pool = Executors.newFixedThreadPool(callers);
        final CountDownLatch ready = new CountDownLatch(callers);
        final CountDownLatch go = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(callers);
        final AtomicInteger successCount = new AtomicInteger();
        final Instant now = Instant.parse("2024-01-01T00:00:00Z");

        for (int i = 0; i < callers; i++) {
            pool.execute(() -> {
                ready.countDown();
                try {
                    go.await(10, TimeUnit.SECONDS);
                    if (adapter.claim("concurrent-key", "fingerprint", now, now.plusSeconds(3600))) {
                        successCount.incrementAndGet();
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        go.countDown();
        assertTrue(done.await(20, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(1, successCount.get(), "exactly one of many concurrent claims for the same key must win");
    }
}
