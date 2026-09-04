package com.pedrolima.wexchange.adapter.in.web.ratelimit;

import com.pedrolima.wexchange.adapter.in.web.ratelimit.AbuseControlProperties.RouteLimit;
import com.pedrolima.wexchange.domain.error.PayloadTooLargeException;
import com.pedrolima.wexchange.domain.error.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Bounds anonymous resource consumption at the web boundary (issue #17):
 * request-body size, page size and sort-field allowlist, and per-route plus
 * global rate limits. Runs as a {@link HandlerInterceptor} rather than a
 * servlet {@link jakarta.servlet.Filter} specifically so a rejection can be
 * thrown as a plain exception and land in {@code GlobalExceptionHandler}
 * exactly like every other error this API produces - a {@code Filter} sits
 * outside Spring MVC's exception-resolution machinery and would have to build
 * its own RFC 9457 response by hand.
 *
 * <p>Client identity is the raw {@link HttpServletRequest#getRemoteAddr()}.
 * Per ADR 0002, no reverse proxy sits in front of this application today and
 * {@code server.forward-headers-strategy} is {@code none}, so no
 * {@code X-Forwarded-*} header is trusted here either - honouring one without
 * a matching trusted-proxy allowlist would let a caller spoof its own rate
 * limit.
 */
public class AbuseControlInterceptor implements HandlerInterceptor {

    private static final Set<String> COUNTRY_CURRENCY_SORT_PROPERTIES = Set.of("countryCurrency", "country", "currency");

    private final AbuseControlProperties properties;

    private final PerKeyRateLimiter rateLimiter;

    private final AbuseControlMetrics metrics;

    public AbuseControlInterceptor(
            final AbuseControlProperties properties,
            final PerKeyRateLimiter rateLimiter,
            final AbuseControlMetrics metrics
    ) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
    }

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler) {
        final Route route = Route.classify(request.getMethod(), request.getRequestURI());

        if (route == Route.PURCHASE_CREATION) {
            enforceBodySizeLimit(request, route);
        }
        if (route == Route.COUNTRY_CURRENCIES) {
            enforceQueryLimits(request, route);
        }
        if (properties.rateLimitEnabled()) {
            enforceRateLimit(request, route);
        }
        return true;
    }

    private void enforceBodySizeLimit(final HttpServletRequest request, final Route route) {
        final long contentLength = request.getContentLengthLong();
        if (contentLength > properties.maxRequestBodyBytes()) {
            metrics.incrementPayloadTooLargeRejection(route);
            throw new PayloadTooLargeException(
                    "Request body exceeds the maximum allowed size of " + properties.maxRequestBodyBytes() + " bytes");
        }
    }

    private void enforceQueryLimits(final HttpServletRequest request, final Route route) {
        enforcePageSize(request, route);
        enforceSortAllowlist(request, route);
        enforceFilterLength(request, route);
    }

    private void enforcePageSize(final HttpServletRequest request, final Route route) {
        final String sizeParam = request.getParameter("size");
        if (sizeParam == null) {
            return;
        }
        final int size;
        try {
            size = Integer.parseInt(sizeParam);
        } catch (final NumberFormatException e) {
            return; // Spring Data's own binding already rejects a non-numeric size; not this interceptor's concern.
        }
        if (size > properties.maxPageSize()) {
            metrics.incrementInvalidQueryRejection(route);
            throw new IllegalArgumentException("size must not exceed " + properties.maxPageSize());
        }
    }

    private void enforceSortAllowlist(final HttpServletRequest request, final Route route) {
        final String[] sortParams = request.getParameterValues("sort");
        if (sortParams == null) {
            return;
        }
        for (final String sortParam : sortParams) {
            final String property = sortParam.split(",")[0].trim();
            if (!COUNTRY_CURRENCY_SORT_PROPERTIES.contains(property)) {
                metrics.incrementInvalidQueryRejection(route);
                throw new IllegalArgumentException("Unknown sort property: " + property);
            }
        }
    }

    private void enforceFilterLength(final HttpServletRequest request, final Route route) {
        final String countryCurrency = request.getParameter("country_currency");
        if (countryCurrency != null && countryCurrency.length() > properties.maxCountryCurrencyFilterLength()) {
            metrics.incrementInvalidQueryRejection(route);
            throw new IllegalArgumentException(
                    "country_currency must not exceed " + properties.maxCountryCurrencyFilterLength() + " characters");
        }
    }

    private void enforceRateLimit(final HttpServletRequest request, final Route route) {
        final String clientIp = request.getRemoteAddr();

        final var globalDecision = rateLimiter.consume(clientIp + ":global", properties.global());
        if (!globalDecision.allowed()) {
            metrics.incrementRateLimitRejection("global");
            throw new RateLimitExceededException("Global rate limit exceeded", globalDecision.retryAfterSeconds());
        }

        final RouteLimit routeLimit = routeLimitFor(route);
        if (routeLimit == null) {
            return;
        }
        final var routeDecision = rateLimiter.consume(clientIp + ":" + route.name(), routeLimit);
        if (!routeDecision.allowed()) {
            metrics.incrementRateLimitRejection(route.name());
            throw new RateLimitExceededException(
                    "Rate limit exceeded for " + route.name(), routeDecision.retryAfterSeconds());
        }
    }

    private RouteLimit routeLimitFor(final Route route) {
        return switch (route) {
            case PURCHASE_CREATION -> properties.purchaseCreation();
            case CONVERSION -> properties.conversion();
            case COUNTRY_CURRENCIES -> properties.countryCurrencies();
            case OTHER -> null;
        };
    }
}
