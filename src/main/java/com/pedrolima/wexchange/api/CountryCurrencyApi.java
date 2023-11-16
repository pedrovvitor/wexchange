package com.pedrolima.wexchange.api;

import com.pedrolima.wexchange.bean.exchange.CountryCurrencyOutput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

@RequestMapping("v1/country_currencies")
@Tag(name = "Country currencies")
public interface CountryCurrencyApi {

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus
    @Operation(summary = "Retrieve all country currencies available in fiscal_service")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Purchase conversion was successful"),
            @ApiResponse(responseCode = "500", description = "An internal server error was thrown")
    })
    ResponseEntity<CountryCurrencyOutput> findAll(@RequestParam(name = "country_currency") String countryCurrency);
}
