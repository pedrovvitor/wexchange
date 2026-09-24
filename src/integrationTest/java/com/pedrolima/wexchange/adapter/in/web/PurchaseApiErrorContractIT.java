package com.pedrolima.wexchange.adapter.in.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pedrolima.wexchange.application.ConvertPurchaseUseCase;
import com.pedrolima.wexchange.application.CreatePurchaseUseCase;
import com.pedrolima.wexchange.application.GetPurchaseUseCase;
import com.pedrolima.wexchange.domain.error.ExchangeRateNotFoundException;
import com.pedrolima.wexchange.domain.error.IdempotencyKeyConflictException;
import com.pedrolima.wexchange.domain.error.MultipleCountryCurrenciesException;
import com.pedrolima.wexchange.domain.error.PurchaseConversionException;
import com.pedrolima.wexchange.domain.error.ResourceNotFoundException;
import com.pedrolima.wexchange.domain.error.RetryableException;
import com.pedrolima.wexchange.domain.exchange.ExchangeRate;
import com.pedrolima.wexchange.domain.money.Money;
import com.pedrolima.wexchange.domain.purchase.Purchase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterization of the RFC 9457 HTTP error contract: controller, request
 * validation, JSON binding, and {@link GlobalExceptionHandler} working
 * together.
 *
 * <p>The suite is offline: all three use cases are stubbed and no servlet
 * container, database, or socket is involved.
 *
 * <p>{@code 429 Too Many Requests} is not covered here. No component in the
 * application produces it yet - issue #17 (rate limiting) is what introduces
 * the first 429-producing code path, and that is where its contract test
 * belongs.
 */
class PurchaseApiErrorContractIT {

    private static final String PURCHASE_ID = "8f0a5cd1-4bd4-4f39-9f4c-1c2d3e4f5a6b";

    private static final String PURCHASE_PATH = "/v1/purchases/" + PURCHASE_ID;

    private static final String CONVERT_PATH = PURCHASE_PATH + "/convert";

    private static final String PROBLEM_JSON = "application/problem+json";

    private CreatePurchaseUseCase createPurchaseUseCase;

    private GetPurchaseUseCase getPurchaseUseCase;

    private ConvertPurchaseUseCase convertPurchaseUseCase;

    private MockMvc mockMvc;

    private static Purchase aPurchase() {
        return Purchase.create(PURCHASE_ID, "A valid description", LocalDate.of(2024, 1, 31),
                Money.of("10.00"), Instant.parse("2024-01-31T12:00:00Z"));
    }

    @BeforeEach
    void setUp() {
        createPurchaseUseCase = mock(CreatePurchaseUseCase.class);
        getPurchaseUseCase = mock(GetPurchaseUseCase.class);
        convertPurchaseUseCase = mock(ConvertPurchaseUseCase.class);

        final var objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .registerModule(new JavaTimeModule())
                // Mirrors ObjectMapperConfig: without this mixin, ProblemDetail's
                // extension properties (code, traceId, violations) never reach
                // the serialized body.
                .addMixIn(org.springframework.http.ProblemDetail.class,
                        org.springframework.http.converter.json.ProblemDetailJacksonMixin.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new PurchaseController(createPurchaseUseCase, getPurchaseUseCase, convertPurchaseUseCase))
                .addFilters(new TraceIdFilter())
                .setControllerAdvice(new GlobalExceptionHandler(new GlobalExceptionMetrics(new SimpleMeterRegistry())))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    // -- creation -------------------------------------------------------

    @Test
    @DisplayName("a valid purchase request returns 201 with a Location header that resolves to the created resource")
    void givenValidPurchase_whenCreating_then201WithCanonicalLocation() throws Exception {
        when(createPurchaseUseCase.execute(any(), any(), any(), any())).thenReturn(aPurchase());

        mockMvc.perform(post("/v1/purchases")
                        .contentType("application/json")
                        .content("""
                                {"description":"A valid description","date":"2024-01-31","amount":10.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.endsWith("/v1/purchases/" + PURCHASE_ID)))
                .andExpect(jsonPath("$.description").value("A valid description"));
    }

    @Test
    @DisplayName("an Idempotency-Key header is forwarded to the use case")
    void givenAnIdempotencyKeyHeader_whenCreating_thenItIsForwardedToTheUseCase() throws Exception {
        when(createPurchaseUseCase.execute(any(), any(), any(), any())).thenReturn(aPurchase());

        mockMvc.perform(post("/v1/purchases")
                        .header("Idempotency-Key", "a-client-key")
                        .contentType("application/json")
                        .content("""
                                {"description":"A valid description","date":"2024-01-31","amount":10.00}
                                """))
                .andExpect(status().isCreated());

        org.mockito.Mockito.verify(createPurchaseUseCase)
                .execute("A valid description", LocalDate.of(2024, 1, 31), new BigDecimal("10.00"), "a-client-key");
    }

    // A malformed Idempotency-Key header depends on the @Validated method-parameter
    // validation Spring only applies to a real, container-managed bean proxy - not
    // to a standalone MockMvc controller built with `new`. That case is covered
    // end to end instead, in PurchaseIdempotencyIT against the real application.

    @Test
    @DisplayName("reusing an Idempotency-Key with a different request returns 409")
    void givenAReusedIdempotencyKeyWithADifferentRequest_whenCreating_then409Problem() throws Exception {
        when(createPurchaseUseCase.execute(any(), any(), any(), any()))
                .thenThrow(new IdempotencyKeyConflictException(
                        "Idempotency-Key a-client-key was already used with a different request"));

        mockMvc.perform(post("/v1/purchases")
                        .header("Idempotency-Key", "a-client-key")
                        .contentType("application/json")
                        .content("""
                                {"description":"A valid description","date":"2024-01-31","amount":10.00}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("idempotency-key-conflict"));
    }

    @Test
    @DisplayName("malformed JSON returns a 400 problem")
    void givenMalformedJson_whenCreating_then400Problem() throws Exception {
        mockMvc.perform(post("/v1/purchases")
                        .contentType("application/json")
                        .content("{\"description\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("malformed-request-body"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("an unknown field is rejected now that the public boundary is strict")
    void givenUnknownField_whenCreating_then400Problem() throws Exception {
        mockMvc.perform(post("/v1/purchases")
                        .contentType("application/json")
                        .content("""
                                {"description":"A valid description","date":"2024-01-31","amount":10.00,"unexpected":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("malformed-request-body"));
    }

    @Test
    @DisplayName("a body violating Bean Validation returns 400 listing the offending field")
    void givenInvalidBody_whenCreating_then400WithViolations() throws Exception {
        mockMvc.perform(post("/v1/purchases")
                        .contentType("application/json")
                        .content("""
                                {"description":"ab","date":"2024-01-31","amount":10.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("validation-failed"))
                .andExpect(jsonPath("$.violations[?(@.field == 'description')]").exists());
    }

    @Test
    @DisplayName("an amount with more than two decimal places is rejected")
    void givenOverPrecisionAmount_whenCreating_then400WithViolations() throws Exception {
        mockMvc.perform(post("/v1/purchases")
                        .contentType("application/json")
                        .content("""
                                {"description":"A valid description","date":"2024-01-31","amount":10.005}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[?(@.field == 'amount')]").exists());
    }

    @Test
    @DisplayName("an unparsable date returns 400 rather than a server error")
    void givenUnparsableDate_whenCreating_then400Problem() throws Exception {
        mockMvc.perform(post("/v1/purchases")
                        .contentType("application/json")
                        .content("""
                                {"description":"A valid description","date":"31-01-2024","amount":10.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-date-format"));
    }

    @Test
    @DisplayName("an unsupported content type returns 415")
    void givenUnsupportedContentType_whenCreating_then415Problem() throws Exception {
        mockMvc.perform(post("/v1/purchases")
                        .contentType("text/plain")
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("unsupported-media-type"));
    }

    @Test
    @DisplayName("an unsupported HTTP method on an existing route returns 405")
    void givenUnsupportedMethod_whenCallingPurchases_then405Problem() throws Exception {
        mockMvc.perform(delete("/v1/purchases"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("method-not-allowed"));
    }

    // -- retrieval --------------------------------------------------------

    @Test
    @DisplayName("a known purchase id returns 200 with the purchase")
    void givenKnownPurchase_whenGetting_then200() throws Exception {
        when(getPurchaseUseCase.execute(PURCHASE_ID)).thenReturn(aPurchase());

        mockMvc.perform(get(PURCHASE_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PURCHASE_ID))
                .andExpect(jsonPath("$.description").value("A valid description"));
    }

    @Test
    @DisplayName("an unknown purchase id returns 404")
    void givenUnknownPurchase_whenGetting_then404Problem() throws Exception {
        when(getPurchaseUseCase.execute(PURCHASE_ID))
                .thenThrow(new ResourceNotFoundException("Purchase not found for id: " + PURCHASE_ID));

        mockMvc.perform(get(PURCHASE_PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("resource-not-found"));
    }

    @Test
    @DisplayName("a non-UUID purchase id returns 400 rather than reaching the use case")
    void givenInvalidUuid_whenGetting_then400Problem() throws Exception {
        mockMvc.perform(get("/v1/purchases/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("type-mismatch"));

        org.mockito.Mockito.verifyNoInteractions(getPurchaseUseCase);
    }

    // -- conversion ---------------------------------------------------------

    @Test
    @DisplayName("a valid conversion request returns 200 with the converted amount")
    void givenKnownPurchase_whenConverting_then200WithConvertedAmount() throws Exception {
        when(convertPurchaseUseCase.execute(any(), any()))
                .thenReturn(aPurchase().convertWith(
                        new ExchangeRate("Brazil-Real", LocalDate.of(2024, 1, 31), new BigDecimal("4.925"))));

        mockMvc.perform(get(CONVERT_PATH).param("country_currency", "Brazil-Real"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PURCHASE_ID))
                .andExpect(jsonPath("$.convertedAmount").value(49.25))
                .andExpect(jsonPath("$.conversionCountryCurrency").value("Brazil-Real"));
    }

    @Test
    @DisplayName("a non-UUID purchase id on conversion returns 400 rather than reaching the use case")
    void givenInvalidUuid_whenConverting_then400Problem() throws Exception {
        mockMvc.perform(get("/v1/purchases/not-a-uuid/convert").param("country_currency", "Brazil-Real"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("type-mismatch"));

        org.mockito.Mockito.verifyNoInteractions(convertPurchaseUseCase);
    }

    @Test
    @DisplayName("a missing country_currency parameter returns 400")
    void givenMissingCountryCurrency_whenConverting_then400Problem() throws Exception {
        mockMvc.perform(get(CONVERT_PATH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("missing-parameter"));
    }

    @Test
    @DisplayName("an unavailable exchange rate returns 404")
    void givenNoExchangeRate_whenConverting_then404Problem() throws Exception {
        when(convertPurchaseUseCase.execute(any(), any()))
                .thenThrow(new ExchangeRateNotFoundException("Exchange rate not found for currency Brazil-Real"));

        mockMvc.perform(get(CONVERT_PATH).param("country_currency", "Brazil-Real"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("exchange-rate-not-found"));
    }

    @Test
    @DisplayName("an ambiguous country-currency returns 409")
    void givenAmbiguousCountryCurrency_whenConverting_then409Problem() throws Exception {
        when(convertPurchaseUseCase.execute(any(), any()))
                .thenThrow(new MultipleCountryCurrenciesException("2 Country currencies found containing Real"));

        mockMvc.perform(get(CONVERT_PATH).param("country_currency", "Real"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ambiguous-country-currency"));
    }

    @Test
    @DisplayName("a rejected country-currency argument returns 400")
    void givenRejectedArgument_whenConverting_then400Problem() throws Exception {
        when(convertPurchaseUseCase.execute(any(), any()))
                .thenThrow(new IllegalArgumentException("Country Currency must have between 3 and 100 characters"));

        mockMvc.perform(get(CONVERT_PATH).param("country_currency", "ab"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-argument"));
    }

    @Test
    @DisplayName("a transient upstream failure returns 503 so callers can retry")
    void givenTransientUpstreamFailure_whenConverting_then503Problem() throws Exception {
        when(convertPurchaseUseCase.execute(any(), any()))
                .thenThrow(new RetryableException("Fiscal Data API is unavailable"));

        mockMvc.perform(get(CONVERT_PATH).param("country_currency", "Brazil-Real"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("upstream-unavailable"));
    }

    @Test
    @DisplayName("an unrecoverable conversion failure returns 500 without leaking internals")
    void givenConversionFailure_whenConverting_then500ProblemWithoutInternals() throws Exception {
        when(convertPurchaseUseCase.execute(any(), any()))
                .thenThrow(new PurchaseConversionException("Unable to convert purchase"));

        mockMvc.perform(get(CONVERT_PATH).param("country_currency", "Brazil-Real"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("purchase-conversion-failed"))
                .andExpect(jsonPath("$.detail").value("Unable to convert the purchase."));
    }

    @Test
    @DisplayName("a truly unexpected failure returns a sanitized 500 that names no internal detail")
    void givenUnclassifiedFailure_whenConverting_then500Sanitized() throws Exception {
        when(convertPurchaseUseCase.execute(any(), any()))
                .thenThrow(new IllegalStateException("Connection to jdbc:postgresql://prod-db:5432 refused"));

        mockMvc.perform(get(CONVERT_PATH).param("country_currency", "Brazil-Real"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal-error"))
                .andExpect(jsonPath("$.detail", Matchers.not(Matchers.containsString("jdbc"))))
                .andExpect(jsonPath("$.detail", Matchers.not(Matchers.containsString("IllegalStateException"))));
    }

    // -- shape, across every handled error --------------------------------

    @Test
    @DisplayName("every problem response carries type, title, status, detail, instance, code, and traceId")
    void givenAnyHandledError_whenResponding_thenTheFullProblemShapeIsPresent() throws Exception {
        when(convertPurchaseUseCase.execute(any(), any()))
                .thenThrow(new ResourceNotFoundException("Purchase not found"));

        mockMvc.perform(get(CONVERT_PATH).param("country_currency", "Brazil-Real"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(Matchers.containsString("resource-not-found")))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Purchase not found"))
                .andExpect(jsonPath("$.instance").value(Matchers.containsString(PURCHASE_ID)))
                .andExpect(jsonPath("$.code").value("resource-not-found"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("a traceId is still produced even when TraceIdFilter did not run for the request")
    void givenNoTraceIdFilter_whenAnErrorOccurs_thenAFreshTraceIdIsStillGenerated() throws Exception {
        final var mockMvcWithoutFilter = MockMvcBuilders
                .standaloneSetup(new PurchaseController(createPurchaseUseCase, getPurchaseUseCase, convertPurchaseUseCase))
                .setControllerAdvice(new GlobalExceptionHandler(new GlobalExceptionMetrics(new SimpleMeterRegistry())))
                .build();
        when(getPurchaseUseCase.execute(PURCHASE_ID))
                .thenThrow(new ResourceNotFoundException("Purchase not found for id: " + PURCHASE_ID));

        mockMvcWithoutFilter.perform(get(PURCHASE_PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("a not-found error never places the raw exception message about the id under $.error")
    void givenAnyHandledError_whenResponding_thenNoLegacyShapeSurvives() throws Exception {
        when(getPurchaseUseCase.execute(PURCHASE_ID))
                .thenThrow(new ResourceNotFoundException("Purchase not found for id: " + PURCHASE_ID));

        mockMvc.perform(get(PURCHASE_PATH))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.path").doesNotExist());
    }
}
