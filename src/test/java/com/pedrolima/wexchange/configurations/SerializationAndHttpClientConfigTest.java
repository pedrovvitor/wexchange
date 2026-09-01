package com.pedrolima.wexchange.configurations;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.pedrolima.wexchange.integration.fiscal.beans.CountryCurrencyInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * These beans are configuration, but their settings are behaviour: rejecting
 * unknown upstream properties would break every Fiscal Data response the moment
 * the provider adds a field, and losing JSR-310 support would break date
 * binding. Both are asserted against the bean the container actually receives.
 */
class SerializationAndHttpClientConfigTest {

    @Test
    @DisplayName("the shared ObjectMapper tolerates new upstream properties")
    void givenConfiguredMapper_whenUnknownPropertyArrives_thenItIsIgnored() throws Exception {
        final var mapper = new ObjectMapperConfig().objectMapper();

        assertFalse(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));

        final var parsed = mapper.readValue(
                "{\"country_currency_desc\":\"Brazil-Real\",\"country\":\"Brazil\","
                        + "\"currency\":\"Real\",\"added_later\":true}",
                CountryCurrencyInput.class);

        assertEquals("Brazil-Real", parsed.countryCurrency());
    }

    @Test
    @DisplayName("the shared ObjectMapper understands java.time types")
    void givenConfiguredMapper_whenReadingIsoDate_thenJsr310ModuleIsRegistered() throws Exception {
        final var mapper = new ObjectMapperConfig().objectMapper();

        assertEquals(LocalDate.of(2024, 1, 31), mapper.readValue("\"2024-01-31\"", LocalDate.class));
    }

    @Test
    @DisplayName("the outbound HTTP client follows redirects")
    void givenConfiguredHttpClient_whenInspectingRedirectPolicy_thenRedirectsAreFollowed() {
        final var httpClient = new HttpClientConfig().httpClient();

        assertNotNull(httpClient);
        assertEquals(HttpClient.Redirect.ALWAYS, httpClient.followRedirects());
    }
}
