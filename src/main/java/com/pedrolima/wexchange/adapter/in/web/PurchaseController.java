package com.pedrolima.wexchange.adapter.in.web;

import com.pedrolima.wexchange.application.ConvertPurchaseUseCase;
import com.pedrolima.wexchange.application.CreatePurchaseUseCase;
import com.pedrolima.wexchange.application.GetPurchaseUseCase;
import com.pedrolima.wexchange.domain.purchase.ConvertedPurchase;
import com.pedrolima.wexchange.domain.purchase.Purchase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Translates between HTTP and the application. The use cases speak in domain
 * types; assembling the response payload and its HATEOAS links is a web concern
 * and stays here.
 *
 * <p>{@code @Validated} lives on this class, not on the {@link PurchaseApi}
 * interface: Spring's method-validation post-processor builds a validating
 * proxy for the bean it is declared on, and an interface-level annotation on a
 * method never triggered one. That gap previously let a blank
 * {@code country_currency} reach the use case unfiltered.
 */
@RestController
@RequiredArgsConstructor
@Validated
public class PurchaseController implements PurchaseApi {

    private static final Map<String, String> CONVERT_PARAMS =
            Map.of("country_currency", "String: Country-Currency to convert");

    private final CreatePurchaseUseCase createPurchaseUseCase;

    private final GetPurchaseUseCase getPurchaseUseCase;

    private final ConvertPurchaseUseCase convertPurchaseUseCase;

    @Override
    public ResponseEntity<PurchaseApiOutput> createPurchase(final CreatePurchaseApiInput input) {
        final Purchase purchase = createPurchaseUseCase.execute(input.description(), input.date(), input.amount());

        final var output = PurchaseApiOutput.with(purchase, createLinks(purchase.id()));
        final var location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(output.id())
                .toUri();

        return ResponseEntity.created(location).body(output);
    }

    @Override
    public ResponseEntity<PurchaseApiOutput> getPurchase(final UUID id) {
        final Purchase purchase = getPurchaseUseCase.execute(id.toString());

        return ResponseEntity.ok(PurchaseApiOutput.with(purchase, createLinks(purchase.id())));
    }

    @Override
    public ResponseEntity<ConvertPurchaseApiOutput> convertPurchase(final UUID id, final String countryCurrency) {
        final ConvertedPurchase converted = convertPurchaseUseCase.execute(id.toString(), countryCurrency);
        final var purchase = converted.purchase();
        final var rate = converted.rate();

        return ResponseEntity.ok(ConvertPurchaseApiOutput.with(
                purchase.id(),
                purchase.description(),
                purchase.purchaseDate().toString(),
                purchase.amount().amount(),
                rate.countryCurrency(),
                rate.rateValue(),
                rate.effectiveDate().toString(),
                converted.convertedAmount().amount(),
                conversionLinks()));
    }

    private static List<ApiLink> createLinks(final String purchaseId) {
        return List.of(
                ApiLink.with("self", "/v1/purchases/" + purchaseId, "GET", Collections.emptyMap()),
                ApiLink.with("convert", "/v1/purchases/" + purchaseId + "/convert?country_currency=",
                        "GET", CONVERT_PARAMS),
                ApiLink.with("country_currencies", "/v1/country_currencies?country_currency=",
                        "GET", CONVERT_PARAMS));
    }

    private static List<ApiLink> conversionLinks() {
        return List.of(
                ApiLink.with("purchase", "/v1/purchases", "POST", Collections.emptyMap()),
                ApiLink.with("country_currencies", "/v1/country_currencies?country_currency=",
                        "GET", CONVERT_PARAMS));
    }
}
