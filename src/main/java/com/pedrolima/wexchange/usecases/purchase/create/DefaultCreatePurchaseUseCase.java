package com.pedrolima.wexchange.usecases.purchase.create;

import com.pedrolima.wexchange.api.ApiLink;
import com.pedrolima.wexchange.api.purchase.CreatePurchaseApiInput;
import com.pedrolima.wexchange.api.purchase.CreatePurchaseApiOutput;
import com.pedrolima.wexchange.entities.PurchaseJpaEntity;
import com.pedrolima.wexchange.repositories.PurchaseRepository;
import com.pedrolima.wexchange.services.async.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service class for handling the creation of new purchases.
 * This class extends {@link CreatePurchaseUseCase} and provides an implementation for the creation
 * of purchase entities, along with the associated business logic.
 * <p>
 * The service performs the following primary operations:
 * - Creates a new {@link PurchaseJpaEntity} from the provided input.
 * - Persists the new purchase entity to the database using {@link PurchaseRepository}.
 * - If this is the first or the only purchase record for the given date, triggers an update of exchange rates
 * through {@link ExchangeRateService}.
 * - Generates related API links for additional actions that can be performed on the newly created purchase,
 * such as currency conversion.
 *
 * @see PurchaseJpaEntity for details on the purchase entity structure.
 * @see CreatePurchaseApiOutput for the output format of the create purchase operation.
 */
@RequiredArgsConstructor
@Service
public class DefaultCreatePurchaseUseCase extends CreatePurchaseUseCase {

    private final PurchaseRepository purchaseRepository;

    private final ExchangeRateService exchangeRateService;

    @Override
    public CreatePurchaseApiOutput execute(final CreatePurchaseApiInput input) {
        final var purchase = PurchaseJpaEntity.newPurchase(input.description(), input.date(), input.amount());
        final var purchaseJpaEntity = purchaseRepository.save(purchase);

        if (purchaseRepository.countByDate(purchase.getDate()) <= 1) {
            exchangeRateService.updateExchangeRates(purchaseJpaEntity);
        }

        final var relatedLinks = createApiLinks(purchaseJpaEntity);

        return CreatePurchaseApiOutput.with(purchaseJpaEntity, relatedLinks);
    }

    private static List<ApiLink> createApiLinks(final PurchaseJpaEntity purchaseJpaEntity) {
        final var convertParams = Map.of(
                "country_currency", "String: Country-Currency to convert"
        );

        return List.of(
                ApiLink.with("convert", "/v1/purchases/" + purchaseJpaEntity.getId() + "/convert?country_currency=",
                        "GET",
                        convertParams),
                ApiLink.with("country_currencies", "/v1/country_currencies?country_currency=", "GET", convertParams)
        );
    }
}
