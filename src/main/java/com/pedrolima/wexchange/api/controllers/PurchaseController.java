package com.pedrolima.wexchange.api.controllers;

import com.pedrolima.wexchange.api.PurchaseApi;
import com.pedrolima.wexchange.purchase.models.ConvertPurchaseApiInput;
import com.pedrolima.wexchange.purchase.models.ConvertPurchaseApiOutput;
import com.pedrolima.wexchange.purchase.models.CreatePurchaseApiInput;
import com.pedrolima.wexchange.purchase.models.CreatePurchaseApiOutput;
import com.pedrolima.wexchange.usecases.purchase.convert.ConvertPurchaseUseCase;
import com.pedrolima.wexchange.usecases.purchase.create.CreatePurchaseUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class PurchaseController implements PurchaseApi {

    private final CreatePurchaseUseCase createPurchaseUseCase;

    private final ConvertPurchaseUseCase convertPurchaseUseCase;

    @Override
    public ResponseEntity<CreatePurchaseApiOutput> createPurchase(final CreatePurchaseApiInput input) {
        final var output = createPurchaseUseCase.execute(input);

        return ResponseEntity.created(URI.create("/purchases/" + output.id() + "/convert?country_currency=")).body(output);
    }

    @Override
    public ResponseEntity<ConvertPurchaseApiOutput> convertPurchase(final String id, final String countryCurrency) {
        final var input = ConvertPurchaseApiInput.with(id, countryCurrency);
        final var output = convertPurchaseUseCase.execute(input);

        return ResponseEntity.ok(output);
    }
}
