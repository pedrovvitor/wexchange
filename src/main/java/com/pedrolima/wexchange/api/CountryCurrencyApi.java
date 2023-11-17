package com.pedrolima.wexchange.api;

import com.pedrolima.wexchange.bean.exchange.CountryCurrencyOutput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
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
    @Operation(
            summary = "Retrieve all country currencies available in fiscal_service",
            description = """
                              This endpoint retrieves a paginated list of all country currencies available in the fiscal service. 
                              It supports pagination to handle large datasets efficiently. Users can optionally filter the results 
                              by specifying a 'country_currency' parameter, which will return only those currencies that contain the 
                              provided string, ignoring case.
                          """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = """
                                      List of country currencies successfully retrieved. The response includes paginated country currency
                                      data along with related links for further actions.
                                  """
            ),
            @ApiResponse(responseCode = "500", description = "An internal server error was thrown, indicating an unexpected condition encountered in the server.")
    })
    ResponseEntity<CountryCurrencyOutput> findByCountryCurrency(
            @Parameter(description = """
                                         Pagination and sorting configuration for the request. Allows control over which page of data is retrieved and how it is sorted.
                                         - 'page': the page number starting from 0 (e.g., page=0 for the first page).
                                         - 'size': the number of records per page (e.g., size=10 for ten records per page).
                                         - 'sort': the sorting criteria in the format 'propertyName,asc|desc' (e.g., sort=name,asc for ascending order by 'name').
                                         Use multiple 'sort' parameters for sorting by multiple properties (e.g., sort=name,asc&sort=id,desc).
                                     """)
            Pageable pageable,
            @Parameter(description = """
                                         Optional filter parameter. When specified, the service will return only those country currencies that
                                         contain the provided string, case-insensitively. Leave blank to retrieve all available country currencies.
                                     """)
            @RequestParam(name = "country_currency", required = false) String countryCurrency);
}
