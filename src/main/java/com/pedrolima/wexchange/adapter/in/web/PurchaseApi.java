package com.pedrolima.wexchange.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@RequestMapping("v1/purchases")
@Tag(name = "Purchases")
public interface PurchaseApi {

    /** Client-supplied idempotency-key format: see docs/adr/0003-purchase-idempotency.md. */
    String IDEMPOTENCY_KEY_PATTERN = "^[A-Za-z0-9_-]{1,255}$";

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new purchase",
            description = "Creates a new purchase record with the given details. Optionally accepts an "
                    + "Idempotency-Key header (issue #18): repeating the same key with the same body replays "
                    + "the original response; repeating it with a different body is a conflict.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created successfully, or replayed from an "
                    + "earlier identical request carrying the same Idempotency-Key"),
            @ApiResponse(responseCode = "400", description = "Invalid input field or malformed Idempotency-Key",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "The Idempotency-Key was already used with a "
                    + "different request body",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported content type",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "A concurrent request with the same Idempotency-Key "
                    + "is still being processed",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<PurchaseApiOutput> createPurchase(
            @Parameter(description = "Purchase details: description, date, and amount.")
            @RequestBody @Valid CreatePurchaseApiInput input,
            @Parameter(description = "Optional client-generated key making this request safely retryable. "
                    + "1-255 characters: letters, digits, hyphens, and underscores.")
            @RequestHeader(name = "Idempotency-Key", required = false)
            @Pattern(regexp = IDEMPOTENCY_KEY_PATTERN) String idempotencyKey
    );

    @GetMapping(value = "{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Retrieve a purchase", description = "Returns a previously recorded purchase.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The purchase was found"),
            @ApiResponse(responseCode = "400", description = "The identifier is not a valid UUID",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No purchase exists for the given id",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<PurchaseApiOutput> getPurchase(
            @Parameter(description = "The purchase identifier, a UUID.")
            @PathVariable(name = "id") UUID id
    );

    @GetMapping(value = "{id}/convert", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Convert a purchase to a given country-currency")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Purchase conversion was successful"),
            @ApiResponse(responseCode = "400", description = "Invalid identifier or country-currency parameter",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Purchase or exchange rate not found",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "The country-currency term is ambiguous",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "The upstream rate provider is unavailable",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    ResponseEntity<ConvertPurchaseApiOutput> convertPurchase(
            @Parameter(description = "The purchase identifier, a UUID.")
            @PathVariable(name = "id") UUID id,
            @Parameter(description = "The country-currency to convert to, e.g. 'Brazil-Real'.")
            @RequestParam(name = "country_currency") @NotBlank String countryCurrency
    );
}
