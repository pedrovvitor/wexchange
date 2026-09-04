package com.pedrolima.wexchange.adapter.in.web.ratelimit;

import com.pedrolima.wexchange.adapter.in.web.ratelimit.AbuseControlProperties.RouteLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbuseControlPropertiesTest {

    @Test
    @DisplayName("a null route limit and non-positive sizes are all defaulted")
    void givenAllNullOrNonPositiveValues_whenConstructing_thenEveryFieldIsDefaulted() {
        final var properties = new AbuseControlProperties(true, null, null, null, null, 0, 0, 0);

        assertEquals(new RouteLimit(20, 100, Duration.ofMinutes(1)), properties.global());
        assertEquals(new RouteLimit(3, 10, Duration.ofMinutes(1)), properties.purchaseCreation());
        assertEquals(new RouteLimit(5, 30, Duration.ofMinutes(1)), properties.conversion());
        assertEquals(new RouteLimit(10, 60, Duration.ofMinutes(1)), properties.countryCurrencies());
        assertEquals(16_384L, properties.maxRequestBodyBytes());
        assertEquals(100, properties.maxPageSize());
        assertEquals(100, properties.maxCountryCurrencyFilterLength());
    }

    @Test
    @DisplayName("explicitly supplied values are kept rather than defaulted")
    void givenExplicitValues_whenConstructing_thenTheyAreKept() {
        final var global = new RouteLimit(1, 2, Duration.ofSeconds(30));
        final var properties = new AbuseControlProperties(false, global, global, global, global, 1, 2, 3);

        assertEquals(global, properties.global());
        assertEquals(1, properties.maxRequestBodyBytes());
        assertEquals(2, properties.maxPageSize());
        assertEquals(3, properties.maxCountryCurrencyFilterLength());
    }

    @Test
    @DisplayName("a non-positive capacity is rejected")
    void givenANonPositiveCapacity_whenConstructingARouteLimit_thenItIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RouteLimit(0, 10, Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("a non-positive refill amount is rejected")
    void givenANonPositiveRefillAmount_whenConstructingARouteLimit_thenItIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RouteLimit(3, 0, Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("a null, zero, or negative refill period is rejected")
    void givenAnInvalidRefillPeriod_whenConstructingARouteLimit_thenItIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RouteLimit(3, 10, null));
        assertThrows(IllegalArgumentException.class, () -> new RouteLimit(3, 10, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new RouteLimit(3, 10, Duration.ofSeconds(-1)));
    }
}
