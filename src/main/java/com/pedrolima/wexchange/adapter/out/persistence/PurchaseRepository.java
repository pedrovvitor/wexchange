package com.pedrolima.wexchange.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface PurchaseRepository extends JpaRepository<PurchaseJpaEntity, String> {

    long countByPurchaseDate(LocalDate purchaseDate);
}
