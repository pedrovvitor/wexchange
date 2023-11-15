package com.pedrolima.wexchange.repositories;

import com.pedrolima.wexchange.entities.CountryCurrencyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CountryCurrencyRepository extends JpaRepository<CountryCurrencyJpaEntity, String> {

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN false ELSE true END FROM CountryCurrencyJpaEntity c WHERE c.countryCurrency = ?1")
    boolean notExistsByCountryCurrency(String countryCurrency);

    @Query("SELECT c FROM CountryCurrencyJpaEntity c WHERE LOWER(c.countryCurrency) LIKE LOWER(CONCAT('%', :countryCurrency, '%'))")
    List<CountryCurrencyJpaEntity> findAllContainingIgnoreCase(@Param("countryCurrency") String countryCurrency);
}
