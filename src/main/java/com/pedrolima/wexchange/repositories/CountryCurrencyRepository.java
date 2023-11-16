package com.pedrolima.wexchange.repositories;

import com.pedrolima.wexchange.entities.CountryCurrencyJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CountryCurrencyRepository extends JpaRepository<CountryCurrencyJpaEntity, String> {

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN false ELSE true END FROM CountryCurrencyJpaEntity c WHERE c.countryCurrency = ?1")
    boolean notExistsByCountryCurrency(String countryCurrency);

    @Query("SELECT c FROM CountryCurrencyJpaEntity c WHERE LOWER(c.countryCurrency) LIKE LOWER(CONCAT('%', :country_currency, '%'))")
    Page<CountryCurrencyJpaEntity> findAllContainingIgnoreCase(Pageable pageable, @Param("country_currency") String countryCurrency);
}
