package com.pedrolima.wexchange.api.controllers;

import com.pedrolima.wexchange.bean.exchange.CountryCurrencyData;
import com.pedrolima.wexchange.bean.exchange.CountryCurrencyOutput;
import com.pedrolima.wexchange.service.CurrenciesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
    private CurrenciesService currenciesService;

    @InjectMocks
    private CountryCurrencyController countryCurrencyController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(countryCurrencyController).build();
    }

    @Test
    void whenCallFindAll_shouldReturnCountryCurrencies() throws Exception {
        List<CountryCurrencyData> countryCurrencyDataList = List.of(
                new CountryCurrencyData("USD - United States Dollar", "United States", "USD"),
                new CountryCurrencyData("EUR - Euro", "European Union", "EUR")
        );
        CountryCurrencyOutput expectedOutput = new CountryCurrencyOutput(countryCurrencyDataList);

        when(currenciesService.getAllExchangeRates())
                .thenReturn(expectedOutput);

        final var request = get("/country_currencies")
                .contentType(MediaType.APPLICATION_JSON);

        final var response = mockMvc.perform(request)
                .andDo(print());

        response.andExpectAll(
                status().isOk(),
                header().string("Content-type", MediaType.APPLICATION_JSON_VALUE),
                jsonPath("$.countryCurrencyDataList[0].country_currency_desc").value("USD - United States Dollar"),
                jsonPath("$.countryCurrencyDataList[0].country").value("United States"),
                jsonPath("$.countryCurrencyDataList[0].currency").value("USD"),
                jsonPath("$.countryCurrencyDataList[1].country_currency_desc").value("EUR - Euro"),
                jsonPath("$.countryCurrencyDataList[1].country").value("European Union"),
                jsonPath("$.countryCurrencyDataList[1].currency").value("EUR")
        );
    }
}

