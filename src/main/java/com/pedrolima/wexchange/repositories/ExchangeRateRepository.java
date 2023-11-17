package com.pedrolima.wexchange.repositories;

import com.pedrolima.wexchange.entities.ExchangeRateCompositeKey;
import com.pedrolima.wexchange.entities.ExchangeRateJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRateJpaEntity, ExchangeRateCompositeKey> {

    @Query("SELECT COUNT(c) = 0 FROM ExchangeRateJpaEntity c WHERE c.countryCurrency = :countryCurrency AND c.effectiveDate "
            + "= :effectiveDate")
    boolean notExistsByCountryCurrencyAndEffectiveDate(
            @Param("countryCurrency") String countryCurrency,
            @Param("effectiveDate") LocalDate effectiveDate
    );

    @Query("SELECT c FROM ExchangeRateJpaEntity c "
            + "WHERE LOWER(c.countryCurrency) LIKE LOWER(CONCAT('%', :countryCurrency, '%')) "
            + "AND c.effectiveDate BETWEEN :startDate AND :endDate "
            + "AND c.effectiveDate = (SELECT MAX(cc.effectiveDate) "
            + "FROM ExchangeRateJpaEntity cc "
            + "WHERE LOWER(cc.countryCurrency) LIKE LOWER(CONCAT('%', :countryCurrency, '%')) "
            + "AND cc.effectiveDate BETWEEN :startDate AND :endDate)")
    List<ExchangeRateJpaEntity> findLatestRatesByCountryCurrencyAndDateRange(
            @Param("countryCurrency") String countryCurrency,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
