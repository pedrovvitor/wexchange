package com.pedrolima.wexchange.adapter.out.fiscal;

import com.pedrolima.wexchange.application.port.CountryCurrencyRecord;
import com.pedrolima.wexchange.application.port.ExchangeRateQuote;
import com.pedrolima.wexchange.domain.error.RetryableException;
import com.pedrolima.wexchange.domain.exchange.ConversionWindow;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link HttpFiscalDataClient} end to end against a local, in-process
 * HTTP server (issue #3) - never the real provider, per the offline-suite rule
 * in {@code docs/engineering/test-taxonomy.md}.
 */
class HttpFiscalDataClientIT {

    private HttpServer server;
    private HttpServer untrustedServer;
    private ExecutorService serverExecutor;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (untrustedServer != null) {
            untrustedServer.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    @Test
    void givenAFetchExchangeRatesCall_whenBuildingTheUpstreamRequest_thenTheQueryPinsFieldsFilterSortAndPageSize()
            throws IOException {
        final AtomicInteger requestCount = new AtomicInteger();
        final AtomicReference<String> observedQuery = new java.util.concurrent.atomic.AtomicReference<>();
        startServer(exchange -> {
            requestCount.incrementAndGet();
            observedQuery.set(exchange.getRequestURI().getQuery());
            sendJson(exchange, 200, dataEnvelope(""));
        });
        final HttpFiscalDataClient client = newClient(defaultProperties());

        client.fetchExchangeRates(new ConversionWindow(LocalDate.of(2024, 1, 15), LocalDate.of(2024, 7, 15)));

        assertEquals(1, requestCount.get());
        assertEquals(
                "page[size]=10000"
                        + "&fields=exchange_rate,effective_date,country_currency_desc"
                        + "&filter=effective_date:gte:2024-01-15,effective_date:lte:2024-07-15"
                        + "&sort=-effective_date,-country_currency_desc",
                observedQuery.get());
    }

    @Test
    void givenAScopedFetchExchangeRatesCall_whenBuildingTheUpstreamRequest_thenTheQueryAlsoPinsTheExactCurrency()
            throws IOException {
        final AtomicInteger requestCount = new AtomicInteger();
        final AtomicReference<String> observedQuery = new java.util.concurrent.atomic.AtomicReference<>();
        startServer(exchange -> {
            requestCount.incrementAndGet();
            observedQuery.set(exchange.getRequestURI().getQuery());
            sendJson(exchange, 200, dataEnvelope(""));
        });
        final HttpFiscalDataClient client = newClient(defaultProperties());

        client.fetchExchangeRates(
                "Brazil-Real", new ConversionWindow(LocalDate.of(2024, 1, 15), LocalDate.of(2024, 7, 15)));

        assertEquals(1, requestCount.get());
        assertEquals(
                "page[size]=10000"
                        + "&fields=exchange_rate,effective_date,country_currency_desc"
                        + "&filter=effective_date:gte:2024-01-15,effective_date:lte:2024-07-15,"
                        + "country_currency_desc:eq:Brazil-Real"
                        + "&sort=-effective_date,-country_currency_desc",
                observedQuery.get());
    }

    @Test
    void givenAFetchCountryCurrenciesCall_whenBuildingTheUpstreamRequest_thenTheQueryPinsFieldsAndPageSize()
            throws IOException {
        final AtomicReference<String> observedQuery = new java.util.concurrent.atomic.AtomicReference<>();
        startServer(exchange -> {
            observedQuery.set(exchange.getRequestURI().getQuery());
            sendJson(exchange, 200, dataEnvelope(""));
        });
        final HttpFiscalDataClient client = newClient(defaultProperties());

        client.fetchCountryCurrencies();

        assertEquals("page[size]=10000&fields=country_currency_desc,currency,country", observedQuery.get());
    }

    @Test
    void givenAResponseWithAnEndlessChainOfNextLinks_whenFetching_thenPaginationStopsAtTheConfiguredCap()
            throws IOException {
        final AtomicInteger requestCount = new AtomicInteger();
        startServer(exchange -> {
            requestCount.incrementAndGet();
            final String selfUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/next";
            sendJson(exchange, 200, "{\"data\":[" + brazilRealJson() + "],\"links\":{\"next\":\"" + selfUrl + "\"}}");
        });
        final FiscalClientProperties properties = new FiscalClientProperties(
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(5),
                1_000_000, 4, 4, Duration.ofMillis(10), 2.0, 0.1,
                Set.of(429, 502, 503, 504), 3, null);
        final HttpFiscalDataClient client = newClient(properties);

        final List<CountryCurrencyRecord> result = client.fetchCountryCurrencies();

        assertEquals(3, requestCount.get());
        assertEquals(3, result.size());
    }

    @Test
    void givenA299Response_whenFetching_thenItIsTreatedAsSuccessNotAsARedirect() throws IOException {
        startServer(exchange -> sendJson(exchange, 299, dataEnvelope(brazilRealJson())));
        final HttpFiscalDataClient client = newClient(defaultProperties());

        final List<CountryCurrencyRecord> result = client.fetchCountryCurrencies();

        assertEquals(1, result.size());
    }

    @Test
    void givenAStatus300Response_whenFetching_thenItIsTreatedAsARedirectRatherThanSuccess() throws IOException {
        startServer(exchange -> {
            if ("/".equals(exchange.getRequestURI().getPath())) {
                exchange.getResponseHeaders().add("Location",
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/redirected");
                exchange.sendResponseHeaders(300, -1);
                exchange.close();
            } else {
                sendJson(exchange, 200, dataEnvelope(brazilRealJson()));
            }
        });
        final HttpFiscalDataClient client = newClient(defaultProperties());

        final List<CountryCurrencyRecord> result = client.fetchCountryCurrencies();

        assertEquals(1, result.size());
    }

    @Test
    void givenTheClientMakesARetriedCall_whenTheCallFinishes_thenResilienceMetricsAreActuallyRecorded()
            throws IOException {
        final AtomicInteger requestCount = new AtomicInteger();
        startServer(exchange -> {
            if (requestCount.incrementAndGet() == 1) {
                sendJson(exchange, 503, "{\"error\":\"unavailable\"}");
            } else {
                sendJson(exchange, 200, dataEnvelope(brazilRealJson()));
            }
        });
        final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        final String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        final HttpFiscalDataClient client = new HttpFiscalDataClient(
                httpClient, defaultProperties(), baseUrl, new FiscalClientMetrics(meterRegistry));

        client.fetchCountryCurrencies();

        final var attemptCounter = meterRegistry.find("wexchange.application.integration.fiscal.retry.attempt.count").counter();
        assertTrue(attemptCounter != null && attemptCounter.count() > 0, "expected the retry-attempt metric to have recorded something");
        final var successTimer = meterRegistry.find("wexchange.application.integration.fiscal.call.duration")
                .tag("outcome", "success").timer();
        assertTrue(successTimer != null && successTimer.count() > 0, "expected a successful-call duration to be recorded");
    }

    @Test
    void givenAClient_whenConstructed_thenItsTotalDeadlineExecutorIsMonitored() throws IOException {
        startServer(exchange -> sendJson(exchange, 200, dataEnvelope(brazilRealJson())));
        final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        final String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        new HttpFiscalDataClient(httpClient, defaultProperties(), baseUrl, new FiscalClientMetrics(meterRegistry));

        final var poolSize = meterRegistry.find("executor.pool.size").tag("name", "fiscal-client-deadline").gauge();
        assertTrue(poolSize != null, "expected the total-deadline executor's pool size to be a monitored gauge");
    }

    @Test
    void givenASuccessfulSinglePageResponse_whenFetchingCountryCurrencies_thenTheyAreReturned() throws IOException {
        startServer(exchange -> sendJson(exchange, 200, dataEnvelope(brazilRealJson())));
        final HttpFiscalDataClient client = newClient(defaultProperties());

        final List<CountryCurrencyRecord> result = client.fetchCountryCurrencies();

        assertEquals(1, result.size());
        assertEquals(new CountryCurrencyRecord("Brazil-Real", "Brazil", "Real"), result.get(0));
    }

    @Test
    void givenATwoPageResponse_whenFetchingCountryCurrencies_thenBothPagesAreAggregated() throws IOException {
        final AtomicInteger requestCount = new AtomicInteger();
        startServer(exchange -> {
            final int callNumber = requestCount.incrementAndGet();
            if (callNumber == 1) {
                final String secondPageUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/page2";
                sendJson(exchange, 200,
                        "{\"data\":[" + brazilRealJson() + "],\"links\":{\"next\":\"" + secondPageUrl + "\"}}");
            } else {
                sendJson(exchange, 200, dataEnvelope(
                        "{\"country_currency_desc\":\"Japan-Yen\",\"country\":\"Japan\",\"currency\":\"Yen\"}"));
            }
        });
        final HttpFiscalDataClient client = newClient(defaultProperties());

        final List<CountryCurrencyRecord> result = client.fetchCountryCurrencies();

        assertEquals(2, result.size());
        assertEquals(2, requestCount.get());
    }

    @Test
    void givenA400Response_whenFetching_thenItFailsWithoutAnyRetry() throws IOException {
        final AtomicInteger requestCount = new AtomicInteger();
        startServer(exchange -> {
            requestCount.incrementAndGet();
            sendJson(exchange, 400, "{\"error\":\"bad request\"}");
        });
        final HttpFiscalDataClient client = newClient(defaultProperties());

        assertThrows(RetryableException.class, client::fetchCountryCurrencies);
        assertEquals(1, requestCount.get());
    }

    @Test
    void givenA400ResponseThatAlsoCarriesALocationHeader_whenFetching_thenItIsStillNeverTreatedAsARedirect()
            throws IOException {
        final AtomicInteger redirectTargetHits = new AtomicInteger();
        startServer(exchange -> {
            if ("/".equals(exchange.getRequestURI().getPath())) {
                exchange.getResponseHeaders().add("Location",
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/redirected");
                sendJson(exchange, 400, "{\"error\":\"bad request\"}");
            } else {
                redirectTargetHits.incrementAndGet();
                sendJson(exchange, 200, dataEnvelope(brazilRealJson()));
            }
        });
        final HttpFiscalDataClient client = newClient(defaultProperties());

        assertThrows(RetryableException.class, client::fetchCountryCurrencies);
        assertEquals(0, redirectTargetHits.get(), "a 400 must never be followed as a redirect, Location header or not");
    }

    @Test
    void givenARedirectTargetRespondingExactlyAtTheSuccessBoundary_whenFetching_thenItSucceeds() throws IOException {
        startServer(exchange -> {
            if ("/".equals(exchange.getRequestURI().getPath())) {
                exchange.getResponseHeaders().add("Location",
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/redirected");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            } else {
                sendJson(exchange, 299, dataEnvelope(brazilRealJson()));
            }
        });
        final HttpFiscalDataClient client = newClient(defaultProperties());

        final List<CountryCurrencyRecord> result = client.fetchCountryCurrencies();

        assertEquals(1, result.size());
    }

    @Test
    void givenARedirectTargetRespondingExactlyAtTheFailureBoundary_whenFetching_thenItFails() throws IOException {
        startServer(exchange -> {
            if ("/".equals(exchange.getRequestURI().getPath())) {
                exchange.getResponseHeaders().add("Location",
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/redirected");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            } else {
                // Valid data at the failure-boundary status: proves the client actually
                // classifies 300 as a failure here, rather than merely happening to fail
                // for some unrelated reason (a body it could not have parsed either way).
                sendJson(exchange, 300, dataEnvelope(brazilRealJson()));
            }
        });
        final HttpFiscalDataClient client = newClient(defaultProperties());

        assertThrows(RetryableException.class, client::fetchCountryCurrencies);
    }

    @Test
    void givenAMeaningfulRetryAfterValue_whenRetrying_thenItIsHonouredRatherThanTheMuchSmallerDefaultBackoff()
            throws IOException {
        final AtomicInteger requestCount = new AtomicInteger();
        startServer(exchange -> {
            if (requestCount.incrementAndGet() == 1) {
                exchange.getResponseHeaders().add("Retry-After", "1");
                sendJson(exchange, 503, "{\"error\":\"unavailable\"}");
            } else {
                sendJson(exchange, 200, dataEnvelope(brazilRealJson()));
            }
        });
        // Default backoff is 10ms; if the real Retry-After value were ignored, this would return in well under 1s.
        final HttpFiscalDataClient client = newClient(defaultProperties());

        final long start = System.nanoTime();
        client.fetchCountryCurrencies();
        final long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertTrue(elapsedMillis >= 900, "expected the 1s Retry-After to be honoured, took only " + elapsedMillis + "ms");
    }

    @Test
    void givenAnInvalidRow_whenFetching_thenTheSchemaRejectionMetricIsIncremented() throws IOException {
        startServer(exchange -> sendJson(exchange, 200, dataEnvelope(
                "{\"country_currency_desc\":\"\",\"country\":\"Nowhere\",\"currency\":\"None\"}")));
        final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        final String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        final HttpFiscalDataClient client = new HttpFiscalDataClient(
                httpClient, defaultProperties(), baseUrl, new FiscalClientMetrics(meterRegistry));

        client.fetchCountryCurrencies();

        final var counter = meterRegistry.find("wexchange.application.integration.fiscal.schema.rejected.count").counter();
        assertTrue(counter != null && counter.count() == 1, "expected exactly one schema-rejection to be recorded");
    }

    @Test
    void givenAnInvalidExchangeRateRow_whenFetching_thenTheSchemaRejectionMetricIsIncremented() throws IOException {
        startServer(exchange -> sendJson(exchange, 200, dataEnvelope(
                "{\"exchange_rate\":\"-1.00\",\"effective_date\":\"2024-01-01\",\"country_currency_desc\":\"Brazil-Real\"}")));
        final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        final String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        final HttpFiscalDataClient client = new HttpFiscalDataClient(
                httpClient, defaultProperties(), baseUrl, new FiscalClientMetrics(meterRegistry));

        client.fetchExchangeRates(new ConversionWindow(LocalDate.of(2023, 7, 1), LocalDate.of(2024, 1, 1)));

        final var counter = meterRegistry.find("wexchange.application.integration.fiscal.schema.rejected.count").counter();
        assertTrue(counter != null && counter.count() == 1, "expected exactly one schema-rejection to be recorded");
    }

    @Test
    void givenTransient503sThenSuccess_whenFetching_thenTheClientRetriesAndEventuallySucceeds() throws IOException {
        final AtomicInteger requestCount = new AtomicInteger();
        startServer(exchange -> {
            if (requestCount.incrementAndGet() <= 2) {
                sendJson(exchange, 503, "{\"error\":\"unavailable\"}");
            } else {
                sendJson(exchange, 200, dataEnvelope(brazilRealJson()));
            }
        });
        final HttpFiscalDataClient client = newClient(defaultProperties());

        final List<CountryCurrencyRecord> result = client.fetchCountryCurrencies();

        assertEquals(1, result.size());
        assertEquals(3, requestCount.get());
    }

    @Test
    void givenA429WithRetryAfter_whenFetching_thenTheProvidersWaitIsHonouredAndItEventuallySucceeds() throws IOException {
        final AtomicInteger requestCount = new AtomicInteger();
        startServer(exchange -> {
            if (requestCount.incrementAndGet() == 1) {
                exchange.getResponseHeaders().add("Retry-After", "0");
                sendJson(exchange, 429, "{\"error\":\"slow down\"}");
            } else {
                sendJson(exchange, 200, dataEnvelope(brazilRealJson()));
            }
        });
        final HttpFiscalDataClient client = newClient(defaultProperties());

        final List<CountryCurrencyRecord> result = client.fetchCountryCurrencies();

        assertEquals(1, result.size());
        assertEquals(2, requestCount.get());
    }

    @Test
    void givenAMalformedJsonBody_whenFetching_thenItFailsWithoutRetryingTheParseFailure() throws IOException {
        final AtomicInteger requestCount = new AtomicInteger();
        startServer(exchange -> {
            requestCount.incrementAndGet();
            sendJson(exchange, 200, "{\"data\":[");
        });
        final HttpFiscalDataClient client = newClient(defaultProperties());

        assertThrows(RetryableException.class, client::fetchCountryCurrencies);
        assertEquals(1, requestCount.get());
    }

    @Test
    void givenAPartiallyInvalidBatch_whenFetching_thenInvalidRowsAreSkippedAndValidOnesAreKept() throws IOException {
        startServer(exchange -> sendJson(exchange, 200, dataEnvelope(
                brazilRealJson() + ","
                        + "{\"country_currency_desc\":\"\",\"country\":\"Nowhere\",\"currency\":\"None\"}")));
        final HttpFiscalDataClient client = newClient(defaultProperties());

        final List<CountryCurrencyRecord> result = client.fetchCountryCurrencies();

        assertEquals(1, result.size());
        assertEquals("Brazil-Real", result.get(0).countryCurrency());
    }

    @Test
    void givenAValidExchangeRateRow_whenFetchingRates_thenAQuoteIsReturned() throws IOException {
        startServer(exchange -> sendJson(exchange, 200, dataEnvelope(
                "{\"exchange_rate\":\"5.22\",\"effective_date\":\"2023-10-30\",\"country_currency_desc\":\"Brazil-Real\"}")));
        final HttpFiscalDataClient client = newClient(defaultProperties());

        final List<ExchangeRateQuote> result = client.fetchExchangeRates(
                new ConversionWindow(LocalDate.of(2023, 7, 1), LocalDate.of(2024, 1, 1)));

        assertEquals(1, result.size());
        assertEquals("Brazil-Real", result.get(0).countryCurrency());
        assertEquals(LocalDate.of(2023, 10, 30), result.get(0).effectiveDate());
    }

    @Test
    void givenNoServerListening_whenFetching_thenAGenuineTransportFailureIsTreatedAsRetryableAndEventuallyFails() {
        final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(200))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        // Nothing is listening on this port: every attempt fails with a genuine IOException.
        final String deadUrl = "http://127.0.0.1:1/";
        final FiscalClientMetrics metrics = new FiscalClientMetrics(new SimpleMeterRegistry());
        final FiscalClientProperties properties = new FiscalClientProperties(
                Duration.ofMillis(200), Duration.ofMillis(200), Duration.ofSeconds(3),
                1_000_000, 4, 2, Duration.ofMillis(10), 2.0, 0.1,
                Set.of(429, 502, 503, 504), 20, null);
        final HttpFiscalDataClient client = new HttpFiscalDataClient(httpClient, properties, deadUrl, metrics);

        assertThrows(RetryableException.class, client::fetchCountryCurrencies);
    }

    @Test
    void givenARedirectWithNoLocationHeader_whenFetching_thenItFailsWithoutFollowingAnything() throws IOException {
        startServer(exchange -> {
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        final HttpFiscalDataClient client = newClient(defaultProperties());

        assertThrows(RetryableException.class, client::fetchCountryCurrencies);
    }

    @Test
    void givenARedirectTargetThatItselfFails_whenFetching_thenTheFailurePropagatesWithoutASecondRedirect() throws IOException {
        startServer(exchange -> {
            if ("/".equals(exchange.getRequestURI().getPath())) {
                exchange.getResponseHeaders().add("Location",
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/redirected");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            } else {
                sendJson(exchange, 500, "{\"error\":\"still broken\"}");
            }
        });
        final HttpFiscalDataClient client = newClient(defaultProperties());

        assertThrows(RetryableException.class, client::fetchCountryCurrencies);
    }

    @Test
    void givenAnUnparsableRetryAfterValue_whenFetching_thenItFallsBackToTheDefaultBackoffInsteadOfFailing()
            throws IOException {
        final AtomicInteger requestCount = new AtomicInteger();
        startServer(exchange -> {
            if (requestCount.incrementAndGet() == 1) {
                exchange.getResponseHeaders().add("Retry-After", "not-a-number");
                sendJson(exchange, 503, "{\"error\":\"unavailable\"}");
            } else {
                sendJson(exchange, 200, dataEnvelope(brazilRealJson()));
            }
        });
        final HttpFiscalDataClient client = newClient(defaultProperties());

        final List<CountryCurrencyRecord> result = client.fetchCountryCurrencies();

        assertEquals(1, result.size());
        assertEquals(2, requestCount.get());
    }

    @Test
    void givenAClient_whenShutdownIsCalled_thenItIsSafeAndIdempotent() throws IOException {
        startServer(exchange -> sendJson(exchange, 200, dataEnvelope(brazilRealJson())));
        final HttpFiscalDataClient client = newClient(defaultProperties());

        client.shutdown();
        client.shutdown();
    }

    @Test
    void givenANonPositiveExchangeRate_whenFetchingRates_thenTheRowIsRejectedRatherThanPersisted() throws IOException {
        startServer(exchange -> sendJson(exchange, 200, dataEnvelope(
                "{\"exchange_rate\":\"-1.00\",\"effective_date\":\"2024-01-01\",\"country_currency_desc\":\"Brazil-Real\"}")));
        final HttpFiscalDataClient client = newClient(defaultProperties());

        final List<ExchangeRateQuote> result = client.fetchExchangeRates(
                new ConversionWindow(LocalDate.of(2023, 7, 1), LocalDate.of(2024, 1, 1)));

        assertTrue(result.isEmpty());
    }

    @Test
    void givenASameOriginRedirect_whenFetching_thenItIsFollowed() throws IOException {
        startServer(exchange -> {
            if ("/".equals(exchange.getRequestURI().getPath())) {
                exchange.getResponseHeaders().add("Location",
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/redirected");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            } else {
                sendJson(exchange, 200, dataEnvelope(brazilRealJson()));
            }
        });
        final HttpFiscalDataClient client = newClient(defaultProperties());

        final List<CountryCurrencyRecord> result = client.fetchCountryCurrencies();

        assertEquals(1, result.size());
    }

    @Test
    void givenACrossOriginRedirect_whenFetching_thenItIsNeverFollowed() throws IOException {
        final AtomicInteger untrustedRequestCount = new AtomicInteger();
        untrustedServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        untrustedServer.createContext("/", exchange -> {
            untrustedRequestCount.incrementAndGet();
            sendJson(exchange, 200, dataEnvelope(brazilRealJson()));
        });
        untrustedServer.start();
        final int untrustedPort = untrustedServer.getAddress().getPort();

        startServer(exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + untrustedPort + "/");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        final HttpFiscalDataClient client = newClient(defaultProperties());

        assertThrows(RetryableException.class, client::fetchCountryCurrencies);
        assertEquals(0, untrustedRequestCount.get());
    }

    @Test
    void givenAResponseSlowerThanTheTotalDeadline_whenFetching_thenItFailsWithinTheDeadline() throws IOException {
        startServer(exchange -> {
            try {
                Thread.sleep(2000);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            sendJson(exchange, 200, dataEnvelope(brazilRealJson()));
        });
        final HttpFiscalDataClient client = newClient(propertiesWithDeadline(Duration.ofMillis(300)));

        final long start = System.nanoTime();
        assertThrows(RetryableException.class, client::fetchCountryCurrencies);
        final long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertTrue(elapsedMillis < 1800, "expected the deadline to cut the call off well before the 2s response: " + elapsedMillis + "ms");
    }

    @Test
    void givenTheCallingThreadIsInterrupted_whenFetching_thenTheInterruptFlagIsPreservedAndFailurePropagates()
            throws IOException, InterruptedException {
        final CountDownLatch requestReceived = new CountDownLatch(1);
        startServer(exchange -> {
            requestReceived.countDown();
            try {
                Thread.sleep(5000);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            sendJson(exchange, 200, dataEnvelope(brazilRealJson()));
        });
        final HttpFiscalDataClient client = newClient(propertiesWithDeadline(Duration.ofSeconds(10)));

        final AtomicBoolean threwRetryable = new AtomicBoolean(false);
        final AtomicBoolean interruptFlagObserved = new AtomicBoolean(false);
        final Thread worker = new Thread(() -> {
            try {
                client.fetchCountryCurrencies();
            } catch (final RetryableException e) {
                threwRetryable.set(true);
                interruptFlagObserved.set(Thread.currentThread().isInterrupted());
            }
        });

        worker.start();
        assertTrue(requestReceived.await(2, TimeUnit.SECONDS), "the request never reached the server");
        worker.interrupt();
        worker.join(5000);

        assertTrue(threwRetryable.get(), "expected a RetryableException from the interrupted call");
        assertTrue(interruptFlagObserved.get(), "expected the interrupt flag to still be set");
    }

    @Test
    void givenMoreConcurrentCallsThanTheBulkheadAllows_whenAnOutageMakesEveryCallSlow_thenUpstreamConcurrencyStaysBounded()
            throws IOException, InterruptedException {
        final int maxConcurrentCalls = 2;
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger maxObservedInFlight = new AtomicInteger();
        final CountDownLatch releaseResponses = new CountDownLatch(1);
        startServer(exchange -> {
            final int current = inFlight.incrementAndGet();
            maxObservedInFlight.updateAndGet(max -> Math.max(max, current));
            try {
                releaseResponses.await(3, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
            }
            sendJson(exchange, 200, dataEnvelope(brazilRealJson()));
        });
        final HttpFiscalDataClient client = newClient(new FiscalClientProperties(
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(2),
                1_000_000, maxConcurrentCalls, 4, Duration.ofMillis(10), 2.0, 0.1,
                Set.of(429, 502, 503, 504), 20, null));

        final int callers = 10;
        final ExecutorService callerPool = Executors.newFixedThreadPool(callers);
        final ConcurrentLinkedQueue<Boolean> outcomes = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < callers; i++) {
            callerPool.execute(() -> {
                try {
                    client.fetchCountryCurrencies();
                    outcomes.add(true);
                } catch (final RuntimeException e) {
                    outcomes.add(false);
                }
            });
        }

        Thread.sleep(500);
        releaseResponses.countDown();
        callerPool.shutdown();
        assertTrue(callerPool.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(callers, outcomes.size());
        assertTrue(maxObservedInFlight.get() <= maxConcurrentCalls,
                "observed " + maxObservedInFlight.get() + " concurrent upstream calls, expected at most " + maxConcurrentCalls);
    }

    @Test
    void givenAnInboundTraceIdOnTheCallingThread_whenFetching_thenTheOutboundRequestCarriesAMatchingTraceparent()
            throws IOException {
        final AtomicReference<String> observedTraceparent = new AtomicReference<>();
        startServer(exchange -> {
            observedTraceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));
            sendJson(exchange, 200, dataEnvelope(brazilRealJson()));
        });
        final HttpFiscalDataClient client = newClient(defaultProperties());

        MDC.put("traceId", "0123456789abcdef0123456789abcdef");
        try {
            client.fetchCountryCurrencies();
        } finally {
            MDC.remove("traceId");
        }

        final String traceparent = observedTraceparent.get();
        assertTrue(traceparent != null && traceparent.matches("00-0123456789abcdef0123456789abcdef-[0-9a-f]{16}-01"),
                "expected a valid traceparent carrying the calling thread's trace id, got: " + traceparent);
    }

    @Test
    void givenNoTraceIdOnTheCallingThread_whenFetching_thenNoTraceparentHeaderIsSent() throws IOException {
        final AtomicReference<String> observedTraceparent = new AtomicReference<>();
        final AtomicBoolean headerWasPresent = new AtomicBoolean(false);
        startServer(exchange -> {
            headerWasPresent.set(exchange.getRequestHeaders().containsKey("traceparent"));
            observedTraceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));
            sendJson(exchange, 200, dataEnvelope(brazilRealJson()));
        });
        final HttpFiscalDataClient client = newClient(defaultProperties());

        client.fetchCountryCurrencies();

        assertTrue(!headerWasPresent.get(), "expected no traceparent header, got: " + observedTraceparent.get());
    }

    // ---------------------------------------------------------------------

    private void startServer(final com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.start();
    }

    private HttpFiscalDataClient newClient(final FiscalClientProperties properties) {
        final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        final String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        final FiscalClientMetrics metrics = new FiscalClientMetrics(new SimpleMeterRegistry());
        return new HttpFiscalDataClient(httpClient, properties, baseUrl, metrics);
    }

    private static FiscalClientProperties defaultProperties() {
        return propertiesWithDeadline(Duration.ofSeconds(5));
    }

    private static FiscalClientProperties propertiesWithDeadline(final Duration totalDeadline) {
        return new FiscalClientProperties(
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                totalDeadline,
                1_000_000,
                4,
                4,
                Duration.ofMillis(10),
                2.0,
                0.1,
                Set.of(429, 502, 503, 504),
                20,
                null);
    }

    private static String brazilRealJson() {
        return "{\"country_currency_desc\":\"Brazil-Real\",\"country\":\"Brazil\",\"currency\":\"Real\"}";
    }

    private static String dataEnvelope(final String rows) {
        return "{\"data\":[" + rows + "]}";
    }

    private static void sendJson(final HttpExchange exchange, final int status, final String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
