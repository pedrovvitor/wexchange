package com.pedrolima.wexchange.adapter.out.persistence;

import com.pedrolima.wexchange.application.port.PurchaseStore;
import com.pedrolima.wexchange.domain.purchase.Purchase;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/** Backs {@link PurchaseStore} with Spring Data, translating at the boundary. */
@Component
public class PurchaseStoreAdapter implements PurchaseStore {

    private final PurchaseRepository repository;

    public PurchaseStoreAdapter(final PurchaseRepository repository) {
        this.repository = repository;
    }

    @Override
    public Purchase save(final Purchase purchase) {
        return repository.save(PurchaseJpaEntity.fromDomain(purchase)).toDomain();
    }

    @Override
    public Optional<Purchase> findById(final String id) {
        return repository.findById(id).map(PurchaseJpaEntity::toDomain);
    }

    @Override
    public long countByPurchaseDate(final LocalDate purchaseDate) {
        return repository.countByPurchaseDate(purchaseDate);
    }
}
