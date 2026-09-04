package com.pedrolima.wexchange.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.time.Instant;
import java.time.LocalDate;

public interface PurchaseRepository extends JpaRepository<PurchaseJpaEntity, String> {

    long countByPurchaseDate(LocalDate purchaseDate);

    @Modifying
    long deleteByCreatedAtBefore(Instant cutoff);
}
