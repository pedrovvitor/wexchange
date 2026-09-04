package com.pedrolima.wexchange.adapter.in.web;

import com.pedrolima.wexchange.application.ConvertPurchaseUseCase;
import com.pedrolima.wexchange.application.CreatePurchaseUseCase;
import com.pedrolima.wexchange.domain.purchase.ConvertedPurchase;
import com.pedrolima.wexchange.domain.purchase.Purchase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Translates between HTTP and the application. The use cases speak in domain
 * types; assembling the response payload and its HATEOAS links is a web concern
 * and stays here.
 */
@RestController
@RequiredArgsConstructor
public class PurchaseController implements PurchaseApi {

    private static final Map<String, String> CONVERT_PARAMS =
            Map.of("country_currency", "String: Country-Currency to convert");

    private final CreatePurchaseUseCase createPurchaseUseCase;

    private final ConvertPurchaseUseCase convertPurchaseUseCase;

    @Override
    public ResponseEntity<CreatePurchaseApiOutput> createPurchase(final CreatePurchaseApiInput input) {
        final Purchase purchase = createPurchaseUseCase.execute(input.description(), input.date(), input.amount());

        final var output = CreatePurchaseApiOutput.with(purchase, createLinks(purchase.id()));

        return ResponseEntity
                .created(URI.create("/purchases/" + output.id() + "/convert?country_currency="))
                .body(output);
    }

    @Override
    public ResponseEntity<ConvertPurchaseApiOutput> convertPurchase(final String id, final String countryCurrency) {
        final ConvertedPurchase converted = convertPurchaseUseCase.execute(id, countryCurrency);
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
