package com.pedrolima.wexchange.api.controllers;

import com.pedrolima.wexchange.integration.fiscal.beans.CountryCurrency;
import com.pedrolima.wexchange.integration.fiscal.beans.CountryCurrencyOutput;
import com.pedrolima.wexchange.services.CountryCurrencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class CountryCurrencyControllerTest {

    @Mock
    private CountryCurrencyService countryCurrencyService;

    @InjectMocks
    private CountryCurrencyController countryCurrencyController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(countryCurrencyController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setViewResolvers((viewName, locale) -> new MappingJackson2JsonView())
                .build();
    }

    @Test
    public void whenCallFindAll_shouldReturnCountryCurrencies() throws Exception {
        final var countryCurrencies = new PageImpl<>(
                List.of(
                        new CountryCurrency("Brazil-Real", "Brazil", "Real"),
                        new CountryCurrency("AFGHANISTAN-AFGHANI", "AFGHANISTAN", "AFGHANI")
                )
        );

        final var expectedOutput = CountryCurrencyOutput.with(countryCurrencies, Collections.emptyList());

        when(countryCurrencyService.findByCountryCurrency(any(), any()))
                .thenReturn(expectedOutput);

        final var request = MockMvcRequestBuilders.get("/v1/country_currencies")
                .contentType(MediaType.APPLICATION_JSON);

        final var response = mockMvc.perform(request)
                .andDo(print());

        response.andExpect(status().isOk())
                .andExpect(header().string("Content-type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.countryCurrencies.content[0].countryCurrency").value("Brazil-Real"))
                .andExpect(jsonPath("$.countryCurrencies.content[0].country").value("Brazil"))
                .andExpect(jsonPath("$.countryCurrencies.content[0].currency").value("Real"))
                .andExpect(jsonPath("$.countryCurrencies.content[1].countryCurrency").value("AFGHANISTAN-AFGHANI"))
                .andExpect(jsonPath("$.countryCurrencies.content[1].country").value("AFGHANISTAN"))
                .andExpect(jsonPath("$.countryCurrencies.content[1].currency").value("AFGHANI"));
    }
}