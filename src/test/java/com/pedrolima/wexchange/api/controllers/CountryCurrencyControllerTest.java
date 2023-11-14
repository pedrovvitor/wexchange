package com.pedrolima.wexchange.api.controllers;

import com.pedrolima.wexchange.bean.exchange.CountryCurrencyData;
import com.pedrolima.wexchange.bean.exchange.CountryCurrencyOutput;
import com.pedrolima.wexchange.service.CountryCurrenciesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CountryCurrencyControllerTest {

    @Mock
    private CountryCurrenciesService countryCurrenciesService;

    @InjectMocks
    private CountryCurrencyController countryCurrencyController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(countryCurrencyController).build();
    }

    @Test
    void whenCallFindAll_shouldReturnCountryCurrencies() throws Exception {
        final var countryCurrencies = List.of(
                new CountryCurrencyData("Brazil-Real", "Brazil", "Real"),
                new CountryCurrencyData("AFGHANISTAN-AFGHANI", "AFGHANISTAN", "AFGHANI")
        );

        final var expectedOutput = new CountryCurrencyOutput(countryCurrencies, Collections.emptyList());

        when(countryCurrenciesService.getAllExchangeRates())
                .thenReturn(expectedOutput);

        final var request = get("/v1/country_currencies")
                .contentType(MediaType.APPLICATION_JSON);

        final var response = mockMvc.perform(request)
                .andDo(print());

        response.andExpectAll(
                status().isOk(),
                header().string("Content-type", MediaType.APPLICATION_JSON_VALUE),
                jsonPath("$.countryCurrencies[0].country_currency_desc").value("Brazil-Real"),
                jsonPath("$.countryCurrencies[0].country").value("Brazil"),
                jsonPath("$.countryCurrencies[0].currency").value("Real"),
                jsonPath("$.countryCurrencies[1].country_currency_desc").value("AFGHANISTAN-AFGHANI"),
                jsonPath("$.countryCurrencies[1].country").value("AFGHANISTAN"),
                jsonPath("$.countryCurrencies[1].currency").value("AFGHANI")
        );
    }
}

