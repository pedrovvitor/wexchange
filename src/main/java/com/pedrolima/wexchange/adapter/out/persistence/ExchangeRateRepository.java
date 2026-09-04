package com.pedrolima.wexchange.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRateJpaEntity, ExchangeRateCompositeKey> {

    /**
     * Every distinct country-currency with at least one eligible rate whose
     * descriptor contains the search term, case-insensitively.
     *
     * <p>Deliberately independent of any single rate's date: resolution answers
     * "how many currencies could this term mean", and mixing that question with
     * a MAX(effectiveDate) comparison is what let two overlapping currencies
     * with different latest dates silently collapse into one match.
     */
    @Query("SELECT DISTINCT c.countryCurrency FROM ExchangeRateJpaEntity c "
            + "WHERE LOWER(c.countryCurrency) LIKE LOWER(CONCAT('%', :countryCurrency, '%')) "
            + "AND c.effectiveDate BETWEEN :startDate AND :endDate")
    List<String> findDistinctCountryCurrenciesInRange(
            @Param("countryCurrency") String countryCurrency,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * The latest eligible rate for one exact, already-resolved country-currency.
     *
     * <p>The MAX(effectiveDate) subquery is scoped to the same exact currency as
     * the outer query, so a second currency's dates can never influence which row
     * this returns.
     */
    @Query("SELECT c FROM ExchangeRateJpaEntity c "
            + "WHERE c.countryCurrency = :countryCurrency "
            + "AND c.effectiveDate BETWEEN :startDate AND :endDate "
            + "AND c.effectiveDate = (SELECT MAX(cc.effectiveDate) "
            + "FROM ExchangeRateJpaEntity cc "
            + "WHERE cc.countryCurrency = :countryCurrency "
            + "AND cc.effectiveDate BETWEEN :startDate AND :endDate)")
    Optional<ExchangeRateJpaEntity> findLatestExactCountryCurrencyInRange(
            @Param("countryCurrency") String countryCurrency,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
