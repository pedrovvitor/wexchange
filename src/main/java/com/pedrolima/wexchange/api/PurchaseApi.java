package com.pedrolima.wexchange.api;

import com.pedrolima.wexchange.api.purchase.ConvertPurchaseApiOutput;
import com.pedrolima.wexchange.api.purchase.CreatePurchaseApiInput;
import com.pedrolima.wexchange.api.purchase.CreatePurchaseApiOutput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.executable.ValidateOnExecution;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

@RequestMapping("v1/purchases")
@Tag(name = "Purchases")
public interface PurchaseApi {

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new Purchase",
            description = "Creates a new purchase record with the given details.",
            tags = {"Purchases"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input field"),
            @ApiResponse(responseCode = "500", description = "An internal server error was thrown")
    })
    ResponseEntity<CreatePurchaseApiOutput> createPurchase(
            @Parameter(description = "Purchase details for creating a new record. Includes description, date, and amount.")
            @RequestBody @Valid CreatePurchaseApiInput input
    );

    @GetMapping(value = "{id}/convert", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Convert a purchase to a given Country-Currency")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Purchase conversion was successful"),
            @ApiResponse(responseCode = "400", description = "Invalid param/id"),
            @ApiResponse(responseCode = "404", description = "Purchase was not found for the given Id"),
            @ApiResponse(responseCode = "409", description = "Multiple country currencies found within the informed param"),
            @ApiResponse(responseCode = "500", description = "An internal server error was thrown")
    })
    @ValidateOnExecution
    @Validated
    ResponseEntity<ConvertPurchaseApiOutput> convertPurchase(
            // TODO: 14/11/2023 fix: validations not working as expected
            @Parameter(description = "The unique identifier of the purchase to be converted.")
            @PathVariable(name = "id") @NotBlank String id,
            @Parameter(description = "The country-currency format in which the purchase amount is to be converted. Should follow the pattern 'Country-Currency'.")
            @RequestParam(name = "country_currency")
            @NotBlank
            String countryCurrency
    );
}
