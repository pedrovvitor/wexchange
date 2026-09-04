package com.pedrolima.wexchange.adapter.out.persistence;

import com.pedrolima.wexchange.application.port.ExchangeRateQuote;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ExchangeRateUpsertRepository} against a real PostgreSQL (issue #6):
 * the {@code ON CONFLICT} clause is the whole point, and only a real engine
 * proves it - a mock would just record that the method was called.
 *
 * <p>{@code upsertAll}'s own {@code @Transactional} (see its Javadoc) is what
 * makes the concurrency test below meaningful: each worker thread has no
 * ambient transaction of its own, so that annotation starts and commits a
 * fresh one per call, letting every thread's write actually land instead of
 * silently evaporating under this application's {@code auto-commit: false}
 * datasource.
 */
@Import(ExchangeRateUpsertRepository.class)
class ExchangeRateUpsertRepositoryIT extends AbstractPostgresRepositoryIT {

    @Autowired
    private ExchangeRateUpsertRepository upsertRepository;

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @Test
    void givenNewRates_whenUpserting_thenTheyArePersisted() {
        final var quote = new ExchangeRateQuote("Brazil-Real", LocalDate.of(2024, 1, 1), new BigDecimal("5.220"));

        upsertRepository.upsertAll(List.of(quote));

        final var stored = exchangeRateRepository
                .findById(new ExchangeRateCompositeKey("Brazil-Real", LocalDate.of(2024, 1, 1)))
                .orElseThrow();
        assertEquals(new BigDecimal("5.220"), stored.getRateValue());
    }

    @Test
    void givenAnExistingRateWithADifferentValue_whenUpsertingACorrection_thenTheStoredValueIsUpdated() {
        upsertRepository.upsertAll(List.of(
                new ExchangeRateQuote("Correction-Coin", LocalDate.of(2024, 2, 1), new BigDecimal("1.000"))));

        upsertRepository.upsertAll(List.of(
                new ExchangeRateQuote("Correction-Coin", LocalDate.of(2024, 2, 1), new BigDecimal("2.500"))));

        final var stored = exchangeRateRepository
                .findById(new ExchangeRateCompositeKey("Correction-Coin", LocalDate.of(2024, 2, 1)))
                .orElseThrow();
        assertEquals(new BigDecimal("2.500"), stored.getRateValue());
    }

    @Test
    void givenAnExistingRateWithTheSameValue_whenUpsertingAgain_thenItSucceedsWithoutError() {
        final var quote = new ExchangeRateQuote("Stable-Coin", LocalDate.of(2024, 3, 1), new BigDecimal("1.000"));
        upsertRepository.upsertAll(List.of(quote));

        upsertRepository.upsertAll(List.of(quote));

        final var stored = exchangeRateRepository
                .findById(new ExchangeRateCompositeKey("Stable-Coin", LocalDate.of(2024, 3, 1)))
                .orElseThrow();
        assertEquals(new BigDecimal("1.000"), stored.getRateValue());
    }

    @Test
    void givenDuplicateKeysWithinTheSameBatch_whenUpserting_thenOnlyOneRowResults() {
        final var effectiveDate = LocalDate.of(2024, 4, 1);
        final var first = new ExchangeRateQuote("Batch-Coin", effectiveDate, new BigDecimal("1.000"));
        final var second = new ExchangeRateQuote("Batch-Coin", effectiveDate, new BigDecimal("2.000"));

        upsertRepository.upsertAll(List.of(first, second));

        final var stored = exchangeRateRepository
                .findById(new ExchangeRateCompositeKey("Batch-Coin", effectiveDate))
                .orElseThrow();
        assertEquals(new BigDecimal("2.000"), stored.getRateValue());
    }

    @Test
    void givenAnEmptyBatch_whenUpserting_thenItIsANoOp() {
        upsertRepository.upsertAll(List.of());
    }

    @Test
    void givenTwentyConcurrentUpsertsForTheSameKey_whenRefreshing_thenExactlyOneRowResultsWithoutIntegrityErrors()
            throws InterruptedException {
        final var effectiveDate = LocalDate.of(2024, 5, 1);
        final int callers = 20;
        final ExecutorService pool = Executors.newFixedThreadPool(callers);
        final CountDownLatch ready = new CountDownLatch(callers);
        final CountDownLatch go = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(callers);
        final java.util.Queue<RuntimeException> failures = new java.util.concurrent.ConcurrentLinkedQueue<>();

        for (int i = 0; i < callers; i++) {
            final int attempt = i;
            pool.execute(() -> {
                ready.countDown();
                try {
                    go.await(10, TimeUnit.SECONDS);
                    upsertRepository.upsertAll(List.of(new ExchangeRateQuote(
                            "Concurrent-Coin", effectiveDate, new BigDecimal(attempt + 1 + ".000"))));
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (final RuntimeException e) {
                    failures.add(e);
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        go.countDown();
        assertTrue(done.await(20, TimeUnit.SECONDS));
        pool.shutdown();

        assertTrue(failures.isEmpty(), "expected no failures, got: " + failures);
        assertEquals(1, exchangeRateRepository.findAll().stream()
                .filter(e -> "Concurrent-Coin".equals(e.getCountryCurrency()))
                .count());
    }
}
