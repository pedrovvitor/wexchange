package com.pedrolima.wexchange.usecases.purchase.create;

import com.pedrolima.wexchange.api.ApiLink;
import com.pedrolima.wexchange.api.purchase.CreatePurchaseApiInput;
import com.pedrolima.wexchange.api.purchase.CreatePurchaseApiOutput;
import com.pedrolima.wexchange.entities.Purchase;
import com.pedrolima.wexchange.repositories.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class DefaultCreatePurchaseUseCase extends CreatePurchaseUseCase {

    private final PurchaseRepository purchaseRepository;
    @Override
    public CreatePurchaseApiOutput execute(final CreatePurchaseApiInput input) {
        final var purchase = Purchase.newPurchase(input.description(), input.date(), input.amount());
        final var purchaseId = purchaseRepository.save(purchase).getId();
        final var convertParams = Map.of(
                "currency", "String: Code of the currency to convert"
        );

        final var relatedLinks = List.of(
                ApiLink.with("convert", "/purchases/" + purchaseId + "/convert", "GET", convertParams),
                ApiLink.with("country_currencies", "/country_currencies", "GET", Collections.emptyMap())
        );

        return CreatePurchaseApiOutput.with(purchaseId, relatedLinks);
    }
}
