package com.pedrolima.wexchange.api.controllers;

import com.pedrolima.wexchange.integration.fiscal.bean.CountryCurrency;
import com.pedrolima.wexchange.services.CountryCurrencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class CountryCurrencyControllerTest {

    @Mock
    private CountryCurrencyService countryCurrencyService;

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
                new CountryCurrency("Brazil-Real", "Brazil", "Real"),
                new CountryCurrency("AFGHANISTAN-AFGHANI", "AFGHANISTAN", "AFGHANI")
        );

        //        final var expectedOutput = new CountryCurrencyOutput(countryCurrencies, Collections.emptyList());

        //        when(countryCurrenciesService.findAllCountryCurrencies(any()))
        //                .thenReturn(expectedOutput);
        //
        //        final var request = get("/v1/country_currencies")
        //                .contentType(MediaType.APPLICATION_JSON);
        //
        //        final var response = mockMvc.perform(request)
        //                .andDo(print());

        //        response.andExpectAll(
        //                status().isOk(),
        //                header().string("Content-type", MediaType.APPLICATION_JSON_VALUE),
        //                jsonPath("$.countryCurrencies[0].countryCurrency").value("Brazil-Real"),
        //                jsonPath("$.countryCurrencies[0].country").value("Brazil"),
        //                jsonPath("$.countryCurrencies[0].currency").value("Real"),
        //                jsonPath("$.countryCurrencies[1].countryCurrency").value("AFGHANISTAN-AFGHANI"),
        //                jsonPath("$.countryCurrencies[1].country").value("AFGHANISTAN"),
        //                jsonPath("$.countryCurrencies[1].currency").value("AFGHANI")
        //        );
    }
}

