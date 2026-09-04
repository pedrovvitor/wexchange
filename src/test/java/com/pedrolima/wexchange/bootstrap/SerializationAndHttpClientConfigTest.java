package com.pedrolima.wexchange.bootstrap;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.pedrolima.wexchange.adapter.in.web.CreatePurchaseApiInput;
import com.pedrolima.wexchange.adapter.out.fiscal.FiscalClientProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These beans are configuration, but their settings are behaviour.
 *
 * <p>{@link ObjectMapperConfig} is the public-facing mapper, wired into Spring
 * MVC: it is strict, rejecting fields a client sends that the API does not
 * model, because that boundary carries untrusted input. The Fiscal Data
 * upstream adapter parses with its own, separate, deliberately tolerant mapper
 * in {@code adapter.out.fiscal.JsonUtils} - see {@code JsonUtilsTest} for that
 * side. The two mappers have opposite trust levels and opposite policies for
 * exactly that reason; there is no single "shared" mapper.
 */
class SerializationAndHttpClientConfigTest {

    @Test
    @DisplayName("the public ObjectMapper rejects a field the API does not model")
    void givenConfiguredMapper_whenUnknownFieldArrives_thenItIsRejected() {
        final var mapper = new ObjectMapperConfig().objectMapper();

        assertTrue(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));

        assertThrows(UnrecognizedPropertyException.class, () -> mapper.readValue(
                """
                {"description":"A valid description","date":"2024-01-31","amount":10.00,"unexpected":true}
                """,
                CreatePurchaseApiInput.class));
    }

    @Test
    @DisplayName("the public ObjectMapper understands java.time types")
    void givenConfiguredMapper_whenReadingIsoDate_thenJsr310ModuleIsRegistered() throws Exception {
        final var mapper = new ObjectMapperConfig().objectMapper();

        assertEquals(LocalDate.of(2024, 1, 31), mapper.readValue("\"2024-01-31\"", LocalDate.class));
    }

    @Test
    @DisplayName("the outbound HTTP client never follows redirects automatically")
    void givenConfiguredHttpClient_whenInspectingRedirectPolicy_thenRedirectsAreNotFollowed() {
        final var properties = new FiscalClientProperties(
                Duration.ofSeconds(2), null, null, 0, 0, 0, null, 0, 0, Set.of(), 0, null);

        final var httpClient = new HttpClientConfig().httpClient(properties);

        assertNotNull(httpClient);
        assertEquals(HttpClient.Redirect.NEVER, httpClient.followRedirects());
        assertEquals(Duration.ofSeconds(2), httpClient.connectTimeout().orElseThrow());
    }
}
