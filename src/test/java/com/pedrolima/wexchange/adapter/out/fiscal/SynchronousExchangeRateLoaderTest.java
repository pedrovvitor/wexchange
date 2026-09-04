package com.pedrolima.wexchange.adapter.out.fiscal;

import com.pedrolima.wexchange.adapter.out.persistence.ExchangeRateRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ExchangeRatePersistence}'s persistence rules are already covered by
 * {@link ExchangeRateServiceTest}; what is specific to this class (issue #4)
 * is the coalescing and failure-propagation behaviour a conversion request's
 * synchronous cache-miss load-through relies on.
 */
@ExtendWith(MockitoExtension.class)
class SynchronousExchangeRateLoaderTest {

    private static final ConversionWindow WINDOW =
            new ConversionWindow(LocalDate.of(2024, 1, 15), LocalDate.of(2024, 7, 15));

    @Mock
    private FiscalDataClient fiscalDataClient;

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    private SynchronousExchangeRateLoader loader;

    @BeforeEach
    void setUp() {
        loader = new SynchronousExchangeRateLoader(fiscalDataClient, exchangeRateRepository);
    }

    @AfterEach
    void tearDown() {
        loader.shutdown();
    }

    @Test
    void givenANewRate_whenLoading_thenItIsPersisted() {
        final var quote = new ExchangeRateQuote("Brazil-Real", LocalDate.of(2024, 6, 1), new BigDecimal("5.22"));
        when(fiscalDataClient.fetchExchangeRates("Brazil-Real", WINDOW)).thenReturn(List.of(quote));
        when(exchangeRateRepository.notExistsByCountryCurrencyAndEffectiveDate("Brazil-Real", quote.effectiveDate()))
                .thenReturn(true);

        loader.loadExact("Brazil-Real", WINDOW);

        verify(exchangeRateRepository).saveAll(any());
    }

    @Test
    void givenAnAlreadyStoredRate_whenLoading_thenNothingIsSaved() {
        final var quote = new ExchangeRateQuote("Brazil-Real", LocalDate.of(2024, 6, 1), new BigDecimal("5.22"));
        when(fiscalDataClient.fetchExchangeRates("Brazil-Real", WINDOW)).thenReturn(List.of(quote));
        when(exchangeRateRepository.notExistsByCountryCurrencyAndEffectiveDate(anyString(), any()))
                .thenReturn(false);

        loader.loadExact("Brazil-Real", WINDOW);

        verify(exchangeRateRepository, never()).saveAll(any());
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
        final var releaseLatch = new CountDownLatch(1);
        final var bothCallersArrived = new CountDownLatch(2);

        when(fiscalDataClient.fetchExchangeRates("Brazil-Real", WINDOW)).thenAnswer(invocation -> {
            callCount.incrementAndGet();
            releaseLatch.await(5, TimeUnit.SECONDS);
            return List.of();
        });

        final ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            final Future<?> first = callers.submit(() -> {
                bothCallersArrived.countDown();
                loader.loadExact("Brazil-Real", WINDOW);
            });
            final Future<?> second = callers.submit(() -> {
                bothCallersArrived.countDown();
                loader.loadExact("Brazil-Real", WINDOW);
            });

            bothCallersArrived.await(5, TimeUnit.SECONDS);
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
