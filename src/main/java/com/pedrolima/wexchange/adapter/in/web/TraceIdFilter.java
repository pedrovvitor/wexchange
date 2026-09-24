package com.pedrolima.wexchange.adapter.in.web;

import com.pedrolima.wexchange.application.tracing.TraceParent;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Gives every request a stable identifier that error responses can echo back to
 * the caller as {@code traceId}, and that structured logs for the same request
 * carry too (issue #9), so a single occurrence can be found across both without
 * a full distributed-tracing setup.
 *
 * <p>Reuses the trace-id field of an inbound W3C {@code traceparent} header when
 * the caller sent one, so a request already correlated upstream keeps the same
 * id here; otherwise it mints a fresh, spec-shaped one. Either way, the id is
 * also what {@link com.pedrolima.wexchange.adapter.out.fiscal.HttpFiscalDataClient}
 * forwards as its own outbound {@code traceparent} for calls made while serving
 * this request. This is correlation-id propagation, not span tracking or a
 * tracing backend integration - that remains issue #19's scope.
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ATTRIBUTE = "traceId";

    static final String MDC_KEY = "traceId";

    private static final String TRACEPARENT_HEADER = "traceparent";

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain
    ) throws ServletException, IOException {
        final String inbound = TraceParent.extractTraceId(request.getHeader(TRACEPARENT_HEADER));
        final String traceId = inbound != null ? inbound : TraceParent.newTraceId();
        request.setAttribute(REQUEST_ATTRIBUTE, traceId);
        MDC.put(MDC_KEY, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
