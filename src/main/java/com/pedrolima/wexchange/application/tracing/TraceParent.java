package com.pedrolima.wexchange.application.tracing;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * W3C {@code traceparent} header handling (issue #9), scoped deliberately
 * narrow: this is correlation-id propagation, not distributed tracing. It
 * lets an id travel from an inbound HTTP request through to the outbound
 * call this service makes to the fiscal data provider, and back into the
 * structured logs and Problem Details for that request - all on the plain
 * assumption that both ends can agree on a trace id shaped like the spec
 * expects. It does not create spans, does not talk to a tracing backend, and
 * does not track parent/child relationships across services; that requires
 * real tracing infrastructure (a {@code micrometer-tracing} bridge and a
 * backend to send spans to) and is left to issue #19.
 *
 * <p>Lives in {@code application}, not in either adapter it connects, because
 * both {@code adapter.in.web} (the inbound filter) and {@code adapter.out.fiscal}
 * (the outbound client) need it, and the hexagonal boundary rules only allow
 * adapter-to-adapter reuse to happen through this layer.
 */
public final class TraceParent {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String VERSION = "00";
    private static final String SAMPLED_FLAGS = "01";
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("[0-9a-f]{32}");
    private static final String INVALID_TRACE_ID = "0".repeat(32);

    private TraceParent() {
    }

    /** A fresh, spec-shaped trace id for a request that arrived with none to reuse. */
    public static String newTraceId() {
        return randomHex(32);
    }

    /**
     * The trace-id field of an inbound {@code traceparent} header, or
     * {@code null} if the header is absent or does not parse as a valid
     * (4-field, non-all-zero trace-id) W3C traceparent value.
     */
    public static String extractTraceId(final String traceparentHeaderValue) {
        if (traceparentHeaderValue == null) {
            return null;
        }
        final String[] fields = traceparentHeaderValue.trim().split("-", -1);
        if (fields.length != 4) {
            return null;
        }
        final String traceId = fields[1];
        return isValidTraceId(traceId) ? traceId : null;
    }

    static boolean isValidTraceId(final String traceId) {
        return traceId != null
                && TRACE_ID_PATTERN.matcher(traceId).matches()
                && !INVALID_TRACE_ID.equals(traceId);
    }

    /** A new outbound header value carrying {@code traceId}, with a freshly generated parent (span) id. */
    public static String header(final String traceId) {
        return VERSION + "-" + traceId + "-" + randomHex(16) + "-" + SAMPLED_FLAGS;
    }

    private static String randomHex(final int length) {
        final StringBuilder hex = new StringBuilder(length);
        while (hex.length() < length) {
            hex.append(Integer.toHexString(RANDOM.nextInt(16)));
        }
        return hex.toString();
    }
}
