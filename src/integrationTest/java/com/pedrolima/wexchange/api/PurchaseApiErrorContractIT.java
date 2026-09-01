package com.pedrolima.wexchange.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pedrolima.wexchange.api.controllers.PurchaseController;
import com.pedrolima.wexchange.entities.PurchaseJpaEntity;
import com.pedrolima.wexchange.exceptions.ExchangeRateNotFoundException;
import com.pedrolima.wexchange.exceptions.MultipleCountryCurrenciesException;
import com.pedrolima.wexchange.exceptions.PurchaseConversionException;
import com.pedrolima.wexchange.exceptions.ResourceNotFoundException;
import com.pedrolima.wexchange.exceptions.RetryableException;
import com.pedrolima.wexchange.exceptions.handler.GlobalExceptionHandler;
import com.pedrolima.wexchange.purchase.models.ConvertPurchaseApiInput;
import com.pedrolima.wexchange.purchase.models.ConvertPurchaseApiOutput;
import com.pedrolima.wexchange.purchase.models.CreatePurchaseApiInput;
import com.pedrolima.wexchange.purchase.models.CreatePurchaseApiOutput;
import com.pedrolima.wexchange.usecases.purchase.convert.ConvertPurchaseUseCase;
import com.pedrolima.wexchange.usecases.purchase.create.CreatePurchaseUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterization of the HTTP error contract produced by the purchase inbound
 * adapter: controller, request validation, JSON binding, and
 * {@link GlobalExceptionHandler} working together.
 *
 * <p>This suite locks in today's observable status codes and payload shape so
 * that the RFC 9457 migration in issue #7 becomes a visible, reviewed change
 * rather than an accident. Assertions deliberately avoid Bean Validation message
 * text, which is locale-dependent until issue #1 lands.
 *
 * <p>The suite is offline: both use cases are stubbed and no servlet container,
 * database, or socket is involved.
 */
class PurchaseApiErrorContractIT {

    private static final String PURCHASE_ID = "8f0a5cd1-4bd4-4f39-9f4c-1c2d3e4f5a6b";

    private static final String CONVERT_PATH = "/v1/purchases/" + PURCHASE_ID + "/convert";

    private CreatePurchaseUseCase createPurchaseUseCase;

    private ConvertPurchaseUseCase convertPurchaseUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        createPurchaseUseCase = mock(CreatePurchaseUseCase.class);
        convertPurchaseUseCase = mock(ConvertPurchaseUseCase.class);

        final var objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new PurchaseController(createPurchaseUseCase, convertPurchaseUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("a valid purchase request returns 201 with a Location header pointing at the conversion resource")
    void givenValidPurchase_whenCreating_then201WithConversionLocation() throws Exception {
        when(createPurchaseUseCase.execute(any(CreatePurchaseApiInput.class)))
                .thenReturn(CreatePurchaseApiOutput.with(
                        PurchaseJpaEntity.newPurchase("A valid description", LocalDate.of(2024, 1, 31),
                                new BigDecimal("10.00")),
                        List.of()));

        mockMvc.perform(post("/v1/purchases")
                        .contentType("application/json")
                        .content("""
                                {"description":"A valid description","date":"2024-01-31","amount":10.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.endsWith("/convert?country_currency=")))
                .andExpect(jsonPath("$.description").value("A valid description"));
    }

    @Test
    @DisplayName("a body violating Bean Validation returns 400 naming the offending field")
    void givenInvalidBody_whenCreating_then400NamingTheField() throws Exception {
        mockMvc.perform(post("/v1/purchases")
                        .contentType("application/json")
                        .content("""
                                {"description":"ab","date":"2024-01-31","amount":10.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("description")));
    }

    @Test
    @DisplayName("an unparsable date returns 400 rather than a server error")
    void givenUnparsableDate_whenCreating_then400() throws Exception {
        mockMvc.perform(post("/v1/purchases")
                        .contentType("application/json")
                        .content("""
                                {"description":"A valid description","date":"31-01-2024","amount":10.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("a valid conversion request returns 200 with the converted amount")
    void givenKnownPurchase_whenConverting_then200WithConvertedAmount() throws Exception {
        when(convertPurchaseUseCase.execute(any(ConvertPurchaseApiInput.class)))
                .thenReturn(ConvertPurchaseApiOutput.with(
                        PURCHASE_ID,
                        "A valid description",
                        "2024-01-31",
                        new BigDecimal("10.00"),
                        "Brazil-Real",
                        new BigDecimal("4.925"),
                        "2024-01-31",
                        new BigDecimal("49.25"),
                        List.of()));

        mockMvc.perform(get(CONVERT_PATH).param("country_currency", "Brazil-Real"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PURCHASE_ID))
                .andExpect(jsonPath("$.convertedAmount").value(49.25))
                .andExpect(jsonPath("$.conversionCountryCurrency").value("Brazil-Real"));
    }

    @Test
    @DisplayName("a missing country_currency parameter returns 400")
    void givenMissingCountryCurrency_whenConverting_then400() throws Exception {
        mockMvc.perform(get(CONVERT_PATH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("country_currency")));
    }

    @Test
    @DisplayName("an unknown purchase returns 404")
    void givenUnknownPurchase_whenConverting_then404() throws Exception {
        when(convertPurchaseUseCase.execute(any(ConvertPurchaseApiInput.class)))
                .thenThrow(new ResourceNotFoundException("Purchase not found for id: " + PURCHASE_ID));

        mockMvc.perform(get(CONVERT_PATH).param("country_currency", "Brazil-Real"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString(PURCHASE_ID)));
    }

    @Test
    @DisplayName("an unavailable exchange rate returns 404")
    void givenNoExchangeRate_whenConverting_then404() throws Exception {
        when(convertPurchaseUseCase.execute(any(ConvertPurchaseApiInput.class)))
                .thenThrow(new ExchangeRateNotFoundException("Exchange rate not found for currency Brazil-Real"));

        mockMvc.perform(get(CONVERT_PATH).param("country_currency", "Brazil-Real"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("an ambiguous country-currency returns 409")
    void givenAmbiguousCountryCurrency_whenConverting_then409() throws Exception {
        when(convertPurchaseUseCase.execute(any(ConvertPurchaseApiInput.class)))
                .thenThrow(new MultipleCountryCurrenciesException("2 Country currencies found containing Real"));

        mockMvc.perform(get(CONVERT_PATH).param("country_currency", "Real"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("a rejected country-currency argument returns 400")
    void givenRejectedArgument_whenConverting_then400() throws Exception {
        when(convertPurchaseUseCase.execute(any(ConvertPurchaseApiInput.class)))
                .thenThrow(new IllegalArgumentException("Country Currency must have between 3 and 100 characters"));

        mockMvc.perform(get(CONVERT_PATH).param("country_currency", "ab"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("a transient upstream failure returns 503 so callers can retry")
    void givenTransientUpstreamFailure_whenConverting_then503() throws Exception {
        when(convertPurchaseUseCase.execute(any(ConvertPurchaseApiInput.class)))
                .thenThrow(new RetryableException("Fiscal Data API is unavailable"));

        mockMvc.perform(get(CONVERT_PATH).param("country_currency", "Brazil-Real"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    @DisplayName("an unrecoverable conversion failure returns 500 without leaking internals")
    void givenConversionFailure_whenConverting_then500() throws Exception {
        when(convertPurchaseUseCase.execute(any(ConvertPurchaseApiInput.class)))
                .thenThrow(new PurchaseConversionException("Unable to convert purchase"));

        mockMvc.perform(get(CONVERT_PATH).param("country_currency", "Brazil-Real"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Unable to convert purchase"));
    }

    @Test
    @DisplayName("every error payload reports the requested path and a timestamp")
    void givenAnyHandledError_whenResponding_thenPathAndTimestampArePresent() throws Exception {
        when(convertPurchaseUseCase.execute(any(ConvertPurchaseApiInput.class)))
                .thenThrow(new ResourceNotFoundException("Purchase not found"));

        mockMvc.perform(get(CONVERT_PATH).param("country_currency", "Brazil-Real"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.path").value(org.hamcrest.Matchers.containsString(PURCHASE_ID)))
                .andExpect(jsonPath("$.timestamp").isNumber());
    }
}
