package com.pedrolima.wexchange.usecases.purchase.create;

import com.pedrolima.wexchange.api.purchase.CreatePurchaseApiInput;
import com.pedrolima.wexchange.api.purchase.CreatePurchaseApiOutput;
import com.pedrolima.wexchange.entities.Purchase;
import com.pedrolima.wexchange.repositories.PurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class DefaultCreatePurchaseUseCase extends CreatePurchaseUseCase {

    private final PurchaseRepository purchaseRepository;
    @Override
    public CreatePurchaseApiOutput execute(final CreatePurchaseApiInput input) {
        final var purchase = Purchase.newPurchase(input.description(), input.date(), input.amount());

        return CreatePurchaseApiOutput.with(purchaseRepository.save(purchase).getId());
    }
}
