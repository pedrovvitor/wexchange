package com.pedrolima.wexchange.repositories;

import com.pedrolima.wexchange.entities.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRepository extends JpaRepository<Purchase, String> {

}
