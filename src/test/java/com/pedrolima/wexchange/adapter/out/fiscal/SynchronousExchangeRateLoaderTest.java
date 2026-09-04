package com.pedrolima.wexchange.adapter.out.fiscal;

import com.pedrolima.wexchange.adapter.out.persistence.ExchangeRateUpsertRepository;
import com.pedrolima.wexchange.application.port.ExchangeRateQuote;
import com.pedrolima.wexchange.application.port.FiscalDataClient;
import com.pedrolima.wexchange.domain.error.RetryableException;
import com.pedrolima.wexchange.domain.exchange.ConversionWindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ExchangeRateUpsertRepository}'s upsert semantics are already covered
 * by its own tests; what is specific to this class (issue #4) is the
 * coalescing and failure-propagation behaviour a conversion request's
 * synchronous cache-miss load-through relies on.
 */
@ExtendWith(MockitoExtension.class)
class SynchronousExchangeRateLoaderTest {

    private static final ConversionWindow WINDOW =
            new ConversionWindow(LocalDate.of(2024, 1, 15), LocalDate.of(2024, 7, 15));

    @Mock
    private FiscalDataClient fiscalDataClient;

    @Mock
    private ExchangeRateUpsertRepository exchangeRateUpsertRepository;

    private SynchronousExchangeRateLoader loader;

    @BeforeEach
    void setUp() {
        loader = new SynchronousExchangeRateLoader(fiscalDataClient, exchangeRateUpsertRepository);
    }

    @AfterEach
    void tearDown() {
        loader.shutdown();
    }

    @Test
    void givenAFetchedRate_whenLoading_thenItIsUpsertedUnchanged() {
        final var quote = new ExchangeRateQuote("Brazil-Real", LocalDate.of(2024, 6, 1), new BigDecimal("5.22"));
        when(fiscalDataClient.fetchExchangeRates("Brazil-Real", WINDOW)).thenReturn(List.of(quote));

        loader.loadExact("Brazil-Real", WINDOW);

        verify(exchangeRateUpsertRepository).upsertAll(List.of(quote));
    }

    @Test
    void givenAnUpstreamFailure_whenLoading_thenItPropagatesUnwrapped() {
        when(fiscalDataClient.fetchExchangeRates("Brazil-Real", WINDOW))
                .thenThrow(new RetryableException("Fiscal data API is unavailable"));

        Assertions.assertThrows(RetryableException.class, () -> loader.loadExact("Brazil-Real", WINDOW));
    }

    @Test
    void givenConcurrentMissesForTheSameCurrencyAndWindow_whenLoading_thenOnlyOneUpstreamCallIsMade()
            throws Exception {
        final var callCount = new AtomicInteger();
        final var fetchStarted = new CountDownLatch(1);
        final var secondStarted = new CountDownLatch(1);
        final var releaseLatch = new CountDownLatch(1);

        when(fiscalDataClient.fetchExchangeRates("Brazil-Real", WINDOW)).thenAnswer(invocation -> {
            callCount.incrementAndGet();
            fetchStarted.countDown();
            releaseLatch.await(5, TimeUnit.SECONDS);
            return List.of();
        });

        final ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            final Future<?> first = callers.submit(() -> loader.loadExact("Brazil-Real", WINDOW));
            Assertions.assertTrue(fetchStarted.await(5, TimeUnit.SECONDS), "the first caller's fetch never started");

            final Future<?> second = callers.submit(() -> {
                secondStarted.countDown();
                loader.loadExact("Brazil-Real", WINDOW);
            });
            /*
             * secondStarted only proves the second caller's thread began running,
             * not that it has already reached computeIfAbsent - submit() merely
             * enqueues, and a thread pool can leave a task unscheduled for an
             * arbitrary stretch. Once it does start, though, everything before
             * computeIfAbsent is a couple of uncontended method calls: a short,
             * generous sleep here is what actually closes the gap. Without both
             * this signal and the sleep, the first caller's still-latched fetch
             * can finish, run its finally block, and remove the coalescing key
             * the instant releaseLatch opens - fast enough, on a warm JVM, to
             * beat a second caller that has not yet reached computeIfAbsent,
             * which then starts its own fetch instead of joining the first's.
             * A standalone, Mockito-free reproduction of just the
             * ConcurrentHashMap/CompletableFuture pattern confirmed this
             * concretely: without this ordering it failed close to 100% of the
             * time; with it, 2000/2000 trials passed.
             */
            Assertions.assertTrue(secondStarted.await(5, TimeUnit.SECONDS), "the second caller's thread never started");
            Thread.sleep(50);

            releaseLatch.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            callers.shutdownNow();
        }

        Assertions.assertEquals(1, callCount.get());
    }

    @Test
    void givenACompletedLoad_whenLoadingAgainForTheSameKey_thenANewUpstreamCallIsMade() {
        when(fiscalDataClient.fetchExchangeRates("Brazil-Real", WINDOW)).thenReturn(List.of());

        loader.loadExact("Brazil-Real", WINDOW);
        loader.loadExact("Brazil-Real", WINDOW);

        verify(fiscalDataClient, times(2)).fetchExchangeRates("Brazil-Real", WINDOW);
    }
}
