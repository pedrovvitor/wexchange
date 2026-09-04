package com.pedrolima.wexchange.adapter.out.fiscal;

import com.pedrolima.wexchange.adapter.out.persistence.ExchangeRateRepository;
import com.pedrolima.wexchange.application.port.ExchangeRateLoader;
import com.pedrolima.wexchange.application.port.FiscalDataClient;
import com.pedrolima.wexchange.domain.exchange.ConversionWindow;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads exactly one currency's exchange rates through to the local store,
 * synchronously, on a conversion request's cache miss (issue #4).
 *
 * <p>Concurrent misses for the same currency and window share one upstream
 * call rather than each starting their own: {@link #loadExact} coalesces
 * callers through a map of in-flight futures keyed by currency and window,
 * cleared the moment that one call finishes (successfully or not), so the
 * next genuinely new miss still gets a fresh attempt. Upstream concurrency
 * across different keys is bounded by {@link FiscalDataClient}'s own
 * bulkhead (issue #3) - this class does not add a second, independent limit
 * on top of it.
 */
@Service
@Slf4j
public class SynchronousExchangeRateLoader implements ExchangeRateLoader {

    private final FiscalDataClient fiscalDataClient;
    private final ExchangeRateRepository exchangeRateRepository;
    private final ExecutorService executor;
    private final ConcurrentMap<String, CompletableFuture<Void>> inFlight = new ConcurrentHashMap<>();

    public SynchronousExchangeRateLoader(
            final FiscalDataClient fiscalDataClient,
            final ExchangeRateRepository exchangeRateRepository
    ) {
        this.fiscalDataClient = fiscalDataClient;
        this.exchangeRateRepository = exchangeRateRepository;
        this.executor = Executors.newCachedThreadPool(SynchronousExchangeRateLoader::newLoaderThread);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    @Override
    public void loadExact(final String countryCurrency, final ConversionWindow window) {
        final String key = coalescingKey(countryCurrency, window);
        final CompletableFuture<Void> future =
                inFlight.computeIfAbsent(key, k -> startLoad(k, countryCurrency, window));
        awaitUnwrapped(future);
    }

    private CompletableFuture<Void> startLoad(final String key, final String countryCurrency, final ConversionWindow window) {
        return CompletableFuture.runAsync(() -> {
            try {
                final var quotes = fiscalDataClient.fetchExchangeRates(countryCurrency, window);
                ExchangeRatePersistence.persistNew(quotes, exchangeRateRepository);
            } finally {
                inFlight.remove(key);
            }
        }, executor);
    }

    private static String coalescingKey(final String countryCurrency, final ConversionWindow window) {
        return countryCurrency + '|' + window.start() + '|' + window.end();
    }

    /** Unwraps {@link CompletionException} so the original failure - not a wrapper - reaches the caller. */
    private static void awaitUnwrapped(final CompletableFuture<Void> future) {
        try {
            future.join();
        } catch (final CompletionException wrapped) {
            if (wrapped.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw wrapped;
        }
    }

    private static Thread newLoaderThread(final Runnable task) {
        final Thread thread = new Thread(task, "exchange-rate-loader");
        thread.setDaemon(true);
        return thread;
    }
}
