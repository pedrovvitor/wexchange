package com.pedrolima.wexchange.application.tracing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceParentTest {

    @Test
    @DisplayName("a fresh trace id is exactly 32 lowercase hex characters")
    void whenGeneratingANewTraceId_thenItIsShapedLikeTheSpecRequires() {
        final String traceId = TraceParent.newTraceId();

        assertTrue(traceId.matches("[0-9a-f]{32}"), "expected 32 lowercase hex chars, got: " + traceId);
    }

    @Test
    @DisplayName("a valid traceparent header yields its trace-id field")
    void givenAValidTraceparentHeader_whenExtracting_thenTheTraceIdFieldIsReturned() {
        final String traceId = TraceParent.extractTraceId("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", traceId);
    }

    @Test
    @DisplayName("a null header yields no trace id")
    void givenNoHeader_whenExtracting_thenNullIsReturned() {
        assertNull(TraceParent.extractTraceId(null));
    }

    @Test
    @DisplayName("a header with the wrong number of fields yields no trace id")
    void givenAMalformedHeader_whenExtracting_thenNullIsReturned() {
        assertNull(TraceParent.extractTraceId("not-a-traceparent-value"));
    }

    @Test
    @DisplayName("a header whose trace-id is the wrong length yields no trace id")
    void givenATraceIdOfTheWrongLength_whenExtracting_thenNullIsReturned() {
        assertNull(TraceParent.extractTraceId("00-tooshort-00f067aa0ba902b7-01"));
    }

    @Test
    @DisplayName("a header whose trace-id is all zeroes yields no trace id")
    void givenTheAllZeroInvalidTraceId_whenExtracting_thenNullIsReturned() {
        assertNull(TraceParent.extractTraceId("00-00000000000000000000000000000000-00f067aa0ba902b7-01"));
    }

    @Test
    @DisplayName("a header whose trace-id contains non-hex characters yields no trace id")
    void givenATraceIdWithNonHexCharacters_whenExtracting_thenNullIsReturned() {
        assertNull(TraceParent.extractTraceId("00-zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz-00f067aa0ba902b7-01"));
    }

    @Test
    @DisplayName("an outbound header carries the given trace id, version, and sampled flag")
    void givenATraceId_whenBuildingAnOutboundHeader_thenItMatchesTheSpecShape() {
        final String header = TraceParent.header("4bf92f3577b34da6a3ce929d0e0e4736");

        assertTrue(header.matches("00-4bf92f3577b34da6a3ce929d0e0e4736-[0-9a-f]{16}-01"),
                "expected a spec-shaped header carrying the given trace id, got: " + header);
    }
}
