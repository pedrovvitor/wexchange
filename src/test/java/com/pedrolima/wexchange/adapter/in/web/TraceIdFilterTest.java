package com.pedrolima.wexchange.adapter.in.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    @DisplayName("no inbound traceparent: a fresh, spec-shaped trace id is minted, exposed as an attribute and MDC entry")
    void givenNoInboundTraceparent_whenFiltering_thenAFreshTraceIdIsSetForTheRequest() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/purchases/1");
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final AtomicReference<String> mdcDuringChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> mdcDuringChain.set(MDC.get(TraceIdFilter.MDC_KEY)));

        final String traceId = (String) request.getAttribute(TraceIdFilter.REQUEST_ATTRIBUTE);
        assertTrue(traceId != null && traceId.matches("[0-9a-f]{32}"), "expected a spec-shaped trace id, got: " + traceId);
        assertEquals(traceId, mdcDuringChain.get());
        assertNull(MDC.get(TraceIdFilter.MDC_KEY), "MDC must be cleared once the request completes");
    }

    @Test
    @DisplayName("a valid inbound traceparent is reused as this request's trace id")
    void givenAValidInboundTraceparent_whenFiltering_thenItsTraceIdIsReused() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/purchases/1");
        request.addHeader("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", request.getAttribute(TraceIdFilter.REQUEST_ATTRIBUTE));
    }

    @Test
    @DisplayName("a malformed inbound traceparent is ignored in favour of a freshly minted trace id")
    void givenAMalformedInboundTraceparent_whenFiltering_thenAFreshTraceIdIsMintedInstead() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/purchases/1");
        request.addHeader("traceparent", "garbage");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        final String traceId = (String) request.getAttribute(TraceIdFilter.REQUEST_ATTRIBUTE);
        assertTrue(traceId != null && traceId.matches("[0-9a-f]{32}"), "expected a spec-shaped trace id, got: " + traceId);
    }

    @Test
    @DisplayName("MDC is cleared even when the rest of the chain throws")
    void givenTheChainThrows_whenFiltering_thenMdcIsStillCleared() {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/purchases/1");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () ->
                filter.doFilter(request, response, (req, res) -> {
                    throw new java.io.IOException("boom");
                }));

        assertNull(MDC.get(TraceIdFilter.MDC_KEY));
    }
}
