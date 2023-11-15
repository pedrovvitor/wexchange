package com.pedrolima.wexchange.repositories;

import com.pedrolima.wexchange.entities.PurchaseJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRepository extends JpaRepository<PurchaseJpaEntity, String> {

}
