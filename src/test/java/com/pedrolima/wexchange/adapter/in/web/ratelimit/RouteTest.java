package com.pedrolima.wexchange.adapter.in.web.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteTest {

    @Test
    void givenPostToPurchases_whenClassifying_thenItIsPurchaseCreation() {
        assertEquals(Route.PURCHASE_CREATION, Route.classify("POST", "/v1/purchases"));
    }

    @Test
    void givenGetToAPurchasesConvertPath_whenClassifying_thenItIsConversion() {
        assertEquals(Route.CONVERSION, Route.classify("GET", "/v1/purchases/6e2b8a5d-3f17-4c90-a4e6-70d5c1b8f293/convert"));
    }

    @Test
    void givenGetToCountryCurrencies_whenClassifying_thenItIsCountryCurrencies() {
        assertEquals(Route.COUNTRY_CURRENCIES, Route.classify("GET", "/v1/country_currencies"));
    }

    @Test
    void givenGetToAPurchaseById_whenClassifying_thenItIsOther() {
        assertEquals(Route.OTHER, Route.classify("GET", "/v1/purchases/6e2b8a5d-3f17-4c90-a4e6-70d5c1b8f293"));
    }

    @Test
    void givenGetToPurchases_whenClassifying_thenItIsOtherBecauseCreationIsPostOnly() {
        assertEquals(Route.OTHER, Route.classify("GET", "/v1/purchases"));
    }

    @Test
    void givenAnUnrelatedPath_whenClassifying_thenItIsOther() {
        assertEquals(Route.OTHER, Route.classify("GET", "/actuator/health"));
    }

    @Test
    void givenAPostToAnUnrelatedPath_whenClassifying_thenItIsOther() {
        assertEquals(Route.OTHER, Route.classify("POST", "/v1/country_currencies"));
    }
}
