package com.pedrolima.wexchange.repositories;

import com.pedrolima.wexchange.entities.PurchaseJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface PurchaseRepository extends JpaRepository<PurchaseJpaEntity, String> {

    long countByPurchaseDate(LocalDate purchaseDate);
}
