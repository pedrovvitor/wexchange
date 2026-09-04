package com.pedrolima.wexchange.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Gives every request a stable identifier that error responses can echo back to
 * the caller as {@code traceId}, so a single occurrence can be found in the logs
 * without a full distributed-tracing setup.
 *
 * <p>This is deliberately not W3C trace context propagation or span correlation
 * across services - that is issue #19's scope. It answers one narrower question:
 * "which request produced this error response", using an id available for the
 * whole request rather than one generated fresh inside the exception handler,
 * which would not correlate to anything.
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ATTRIBUTE = "traceId";

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain
    ) throws ServletException, IOException {
        request.setAttribute(REQUEST_ATTRIBUTE, UUID.randomUUID().toString());
        filterChain.doFilter(request, response);
    }
}
