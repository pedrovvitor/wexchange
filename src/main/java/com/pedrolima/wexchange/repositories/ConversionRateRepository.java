package com.pedrolima.wexchange.repositories;

import com.pedrolima.wexchange.entities.ConversionRateCompositeKey;
import com.pedrolima.wexchange.entities.ConversionRateJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ConversionRateRepository extends JpaRepository<ConversionRateJpaEntity, ConversionRateCompositeKey> {

    @Query("SELECT COUNT(c) = 0 FROM ConversionRateJpaEntity c WHERE c.countryCurrency = :countryCurrency AND c.effectiveDate" +
            " = :effectiveDate")
    boolean notExistsByCountryCurrencyAndEffectiveDate(
            @Param("countryCurrency") String countryCurrency,
            @Param("effectiveDate") LocalDate effectiveDate
    );

    @Query("SELECT c FROM ConversionRateJpaEntity c " +
            "WHERE LOWER(c.countryCurrency) LIKE LOWER(CONCAT('%', :countryCurrency, '%')) " +
            "AND c.effectiveDate BETWEEN :startDate AND :endDate " +
            "AND c.effectiveDate = (SELECT MAX(cc.effectiveDate) " +
            "                     FROM ConversionRateJpaEntity cc " +
            "                     WHERE LOWER(cc.countryCurrency) LIKE LOWER(CONCAT('%', :countryCurrency, '%')) " +
            "                     AND cc.effectiveDate BETWEEN :startDate AND :endDate)")
    List<ConversionRateJpaEntity> findLatestRatesByCountryCurrencyAndDateRange(
            @Param("countryCurrency") String countryCurrency,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
