package com.pedrolima.wexchange.adapter.out.fiscal;

import com.pedrolima.wexchange.application.port.CountryCurrencyRecord;
import com.pedrolima.wexchange.application.port.ExchangeRateQuote;
import com.pedrolima.wexchange.application.port.FiscalDataClient;
import com.pedrolima.wexchange.application.tracing.TraceParent;
import com.pedrolima.wexchange.domain.error.RetryableException;
import com.pedrolima.wexchange.domain.exchange.ConversionWindow;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.FieldType.COUNTRY;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.FieldType.COUNTRY_CURRENCY;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.FieldType.CURRENCY;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.FieldType.EFFECTIVE_DATE;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.FieldType.EXCHANGE_RATE;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.PAGE_SIZE_MAX_VALUE;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.PageType.SIZE;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.ParamComparator.EQ;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.ParamComparator.GTE;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.ParamComparator.LTE;
import static com.pedrolima.wexchange.adapter.out.fiscal.ApiUrlBuilder.SortOrder.DESC;

/**
 * The one resilient gateway to the upstream fiscal data provider (issue #3).
 *
 * <p>Every attempt goes through, innermost first: a bulkhead bounding
 * concurrent upstream calls, a circuit breaker that stops calling a provider
 * already known to be failing, and a retry policy that only ever retries
 * transient failures (I/O errors, {@code 429}, and the configured {@code 5xx}
 * codes), honouring a {@code Retry-After} header when the provider sends one.
 * A total deadline wraps the whole operation - including every page and every
 * retry - independently of the per-attempt connect/request timeouts.
 *
 * <p>Retries live here and only here: neither calling service retries what
 * this client already gave up on, so no two retry policies ever stack.
 */
@Service
@Slf4j
public class HttpFiscalDataClient implements FiscalDataClient {

    private static final String TRACE_ID_MDC_KEY = "traceId";
    private static final String TRACEPARENT_HEADER = "traceparent";

    private final HttpClient httpClient;
    private final FiscalClientProperties properties;
    private final String exchangeApiUrl;
    private final URI providerOrigin;
    private final FiscalClientMetrics metrics;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;
    private final ExecutorService deadlineExecutor;

    public HttpFiscalDataClient(
            final HttpClient httpClient,
            final FiscalClientProperties properties,
            @Value("${fiscal.service.api.endpoint}") final String exchangeApiUrl,
            final FiscalClientMetrics metrics
    ) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.exchangeApiUrl = exchangeApiUrl;
        this.providerOrigin = URI.create(exchangeApiUrl);
        this.metrics = metrics;
        final FiscalResiliencePolicies policies = new FiscalResiliencePolicies(properties);
        this.retry = policies.retry();
        this.circuitBreaker = policies.circuitBreaker();
        this.bulkhead = policies.bulkhead();
        this.deadlineExecutor = metrics.monitorExecutor(
                Executors.newCachedThreadPool(HttpFiscalDataClient::newDeadlineThread), "fiscal-client-deadline");
        metrics.bindTo(retry, circuitBreaker, bulkhead);
    }

    @PreDestroy
    void shutdown() {
        deadlineExecutor.shutdownNow();
    }

    @Override
    public List<ExchangeRateQuote> fetchExchangeRates(final ConversionWindow window) {
        return fetchExchangeRates(exchangeRatesUrl(window, null));
    }

    @Override
    public List<ExchangeRateQuote> fetchExchangeRates(final String exactCountryCurrency, final ConversionWindow window) {
        return fetchExchangeRates(exchangeRatesUrl(window, exactCountryCurrency));
    }

    private String exchangeRatesUrl(final ConversionWindow window, final String exactCountryCurrency) {
        final ApiUrlBuilder builder = new ApiUrlBuilder(exchangeApiUrl)
                .addFields(EXCHANGE_RATE, EFFECTIVE_DATE, COUNTRY_CURRENCY)
                .addFilter(EFFECTIVE_DATE, GTE, window.start().toString())
                .addFilter(EFFECTIVE_DATE, LTE, window.end().toString());
        if (exactCountryCurrency != null) {
            builder.addFilter(COUNTRY_CURRENCY, EQ, exactCountryCurrency);
        }
        return builder.addSorting(DESC, EFFECTIVE_DATE)
                .addSorting(DESC, COUNTRY_CURRENCY)
                .addPagination(SIZE, PAGE_SIZE_MAX_VALUE)
                .build();
    }

    private List<ExchangeRateQuote> fetchExchangeRates(final String firstPageUrl) {
        final List<ConversionRate> rows = fetchAllPagesWithDeadline(firstPageUrl, ConversionRate.class);
        final List<ExchangeRateQuote> quotes = new ArrayList<>(rows.size());
        for (final ConversionRate row : rows) {
            mapExchangeRate(row).ifPresent(quotes::add);
        }
        return quotes;
    }

    @Override
    public List<CountryCurrencyRecord> fetchCountryCurrencies() {
        final String firstPageUrl = new ApiUrlBuilder(exchangeApiUrl)
                .addFields(COUNTRY_CURRENCY, CURRENCY, COUNTRY)
                .addPagination(SIZE, PAGE_SIZE_MAX_VALUE)
                .build();

        final List<CountryCurrencyInput> rows = fetchAllPagesWithDeadline(firstPageUrl, CountryCurrencyInput.class);
        final List<CountryCurrencyRecord> records = new ArrayList<>(rows.size());
        for (final CountryCurrencyInput row : rows) {
            mapCountryCurrency(row).ifPresent(records::add);
        }
        return records;
    }

    private Optional<ExchangeRateQuote> mapExchangeRate(final ConversionRate row) {
        try {
            return Optional.of(new ExchangeRateQuote(row.countryCurrency(), row.effectiveDate(), row.exchangeRate()));
        } catch (final RuntimeException invalid) {
            metrics.incrementSchemaRejection();
            log.warn("Rejected an exchange-rate row that failed schema validation: {}", invalid.getMessage());
            return Optional.empty();
        }
    }

    private Optional<CountryCurrencyRecord> mapCountryCurrency(final CountryCurrencyInput row) {
        try {
            return Optional.of(new CountryCurrencyRecord(row.countryCurrency(), row.country(), row.currency()));
        } catch (final RuntimeException invalid) {
            metrics.incrementSchemaRejection();
            log.warn("Rejected a country-currency row that failed schema validation: {}", invalid.getMessage());
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------------
    // Pagination, bounded by a total deadline spanning every page and retry.
    // ---------------------------------------------------------------------

    private <T> List<T> fetchAllPagesWithDeadline(final String firstPageUrl, final Class<T> rowType) {
        final String traceId = MDC.get(TRACE_ID_MDC_KEY);
        final Future<List<T>> future = deadlineExecutor.submit(() -> fetchAllPages(firstPageUrl, rowType, traceId));
        try {
            return future.get(properties.totalDeadline().toMillis(), TimeUnit.MILLISECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new RetryableException("Interrupted while calling the fiscal data API", e);
        } catch (final TimeoutException e) {
            future.cancel(true);
            throw new RetryableException("Fiscal data API call exceeded the configured total deadline", e);
        } catch (final ExecutionException e) {
            throw toRetryableException(e.getCause());
        }
    }

    private <T> List<T> fetchAllPages(final String firstPageUrl, final Class<T> rowType, final String traceId)
            throws IOException {
        final List<T> all = new ArrayList<>();
        String nextUrl = firstPageUrl;
        int pagesFetched = 0;
        while (nextUrl != null && pagesFetched < properties.maxPages()) {
            final RawResponse response = executeWithResilience(buildRequest(nextUrl, traceId), traceId);
            all.addAll(JsonUtils.extractDataList(response.body(), rowType));
            nextUrl = JsonUtils.extractNextPageUrl(response.body()).orElse(null);
            pagesFetched++;
        }
        return all;
    }

    private HttpRequest buildRequest(final String url, final String traceId) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(properties.requestTimeout())
                .GET();
        addTraceParent(builder, traceId);
        return builder.build();
    }

    /**
     * Only added when this call is happening on behalf of an inbound request
     * that already carries a trace id (see {@link #fetchAllPagesWithDeadline});
     * the scheduled country-currency sync runs with no such request in
     * progress and simply sends no {@code traceparent} at all.
     */
    private static void addTraceParent(final HttpRequest.Builder builder, final String traceId) {
        if (traceId != null) {
            builder.header(TRACEPARENT_HEADER, TraceParent.header(traceId));
        }
    }

    // ---------------------------------------------------------------------
    // One HTTP round trip, protected by bulkhead + circuit breaker + retry.
    // The total deadline above wraps this; per-attempt timing is bounded by
    // the request's own HttpRequest#timeout instead.
    // ---------------------------------------------------------------------

    private RawResponse executeWithResilience(final HttpRequest request, final String traceId) {
        final Supplier<RawResponse> attempt = () -> sendOnce(request, traceId);
        final Supplier<RawResponse> decorated = Decorators.ofSupplier(attempt)
                .withBulkhead(bulkhead)
                .withCircuitBreaker(circuitBreaker)
                .withRetry(retry)
                .decorate();
        return decorated.get();
    }

    private RawResponse sendOnce(final HttpRequest request, final String traceId) {
        try {
            final HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (isRedirect(response.statusCode())) {
                return followRedirectOnce(request, response, traceId);
            }
            return toRawResponseOrThrow(response);
        } catch (final IOException e) {
            throw new UpstreamIoException(e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamInterruptedException(e);
        }
    }

    /** Bounded to exactly one hop, and only onto the provider's own origin. */
    private RawResponse followRedirectOnce(
            final HttpRequest original, final HttpResponse<InputStream> redirect, final String traceId)
            throws IOException, InterruptedException {
        consumeQuietly(redirect.body());
        final URI location = redirect.headers().firstValue("Location")
                .map(original.uri()::resolve)
                .orElseThrow(() -> statusException(redirect.statusCode(), redirect.headers()));

        if (!isSameOrigin(location, providerOrigin)) {
            log.warn("Refusing to follow a redirect to an untrusted origin: {}", location.getHost());
            throw statusException(redirect.statusCode(), redirect.headers());
        }

        final HttpRequest.Builder followUpBuilder = HttpRequest.newBuilder(location)
                .timeout(properties.requestTimeout())
                .GET();
        addTraceParent(followUpBuilder, traceId);
        final HttpResponse<InputStream> response =
                httpClient.send(followUpBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
        return toRawResponseOrThrow(response);
    }

    private RawResponse toRawResponseOrThrow(final HttpResponse<InputStream> response) throws IOException {
        final int status = response.statusCode();
        if (!isSuccess(status)) {
            consumeQuietly(response.body());
            throw statusException(status, response.headers());
        }
        return new RawResponse(status, BoundedBodyReader.read(response.body(), properties.maxResponseBytes()));
    }

    static boolean isRedirect(final int status) {
        return status >= 300 && status < 400;
    }

    static boolean isSuccess(final int status) {
        return status >= 200 && status < 300;
    }

    static boolean isSameOrigin(final URI a, final URI b) {
        return Objects.equals(a.getScheme(), b.getScheme())
                && Objects.equals(a.getHost(), b.getHost())
                && effectivePort(a) == effectivePort(b);
    }

    static int effectivePort(final URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /**
     * Discards a body we are not returning (a redirect, a rejected status),
     * without needing to distinguish read failures from a clean drain: either
     * way the outcome is already decided, and the connection is simply not
     * reused if draining fails.
     */
    private static void consumeQuietly(final InputStream body) throws IOException {
        try (body) {
            body.readAllBytes();
        }
    }

    private UpstreamHttpStatusException statusException(final int status, final HttpHeaders headers) {
        final boolean retryable = properties.retryableStatusCodes().contains(status);
        final Duration retryAfter = retryable ? parseRetryAfter(headers) : null;
        return new UpstreamHttpStatusException(status, retryable, retryAfter);
    }

    static Duration parseRetryAfter(final HttpHeaders headers) {
        return headers.firstValue("Retry-After").map(HttpFiscalDataClient::parseRetryAfterValue).orElse(null);
    }

    /** Delta-seconds only, per the fiscal provider's own contract - not the rarer HTTP-date form. */
    static Duration parseRetryAfterValue(final String value) {
        try {
            final long seconds = Long.parseLong(value.trim());
            return seconds >= 0 ? Duration.ofSeconds(seconds) : null;
        } catch (final NumberFormatException notANumber) {
            return null;
        }
    }

    static RetryableException toRetryableException(final Throwable cause) {
        final String description = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        return new RetryableException("Fiscal data API is unavailable (" + description + ")", (Exception) cause);
    }

    private static Thread newDeadlineThread(final Runnable task) {
        final Thread thread = new Thread(task, "fiscal-client-deadline");
        thread.setDaemon(true);
        return thread;
    }
}
