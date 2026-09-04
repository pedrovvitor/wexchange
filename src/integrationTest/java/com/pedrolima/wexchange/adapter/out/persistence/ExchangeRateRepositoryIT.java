package com.pedrolima.wexchange.adapter.out.persistence;

import com.pedrolima.wexchange.bootstrap.Main;
import com.pedrolima.wexchange.domain.exchange.ConversionWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces the ambiguous-match defect from issue #2 directly against Spring
 * Data JPA, because the defect lived in the generated SQL rather than in
 * application logic: a {@code MAX(effectiveDate)} subquery scoped to the whole
 * LIKE-matched set, not to one currency, let two overlapping currencies with
 * different latest dates silently collapse into a single row instead of
 * surfacing as ambiguous.
 *
 * <p>Runs against an embedded H2 database rather than PostgreSQL. Testcontainers
 * and Flyway are issue #5's scope and are not introduced here; the datasource
 * properties below exist only so this test never touches
 * {@code ${DATABASE_POSTGRES_URL}}, which is unresolved outside a real
 * deployment. See docs/engineering/test-taxonomy.md for the tracked gap.
 */
@DataJpaTest
@ContextConfiguration(classes = Main.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:exchange_rate_repository_it;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.connection.provider_disables_autocommit=false",
})
class ExchangeRateRepositoryIT {

    private static final LocalDate PURCHASE_DATE = LocalDate.of(2024, 7, 15);

    private static final ConversionWindow WINDOW = ConversionWindow.endingOn(PURCHASE_DATE);

    @Autowired
    private ExchangeRateRepository repository;

    @Test
    @DisplayName("two overlapping currencies with different latest dates are both reported, not silently collapsed")
    void givenOverlappingCurrenciesWithDifferentLatestDates_whenResolving_thenBothAreCandidates() {
        seed("Brazil-Real", LocalDate.of(2024, 7, 1), "5.000");
        seed("Iran-Real", LocalDate.of(2024, 6, 1), "42.000");

        final var candidates = repository.findDistinctCountryCurrenciesInRange("real", WINDOW.start(), WINDOW.end());

        assertEquals(2, candidates.size());
        assertTrue(candidates.containsAll(List.of("Brazil-Real", "Iran-Real")));
    }

    @Test
    @DisplayName("an exact currency's latest rate is unaffected by another currency's later date")
    void givenAnotherCurrencyWithALaterDate_whenFindingExactLatest_thenItDoesNotLeakIn() {
        seed("Brazil-Real", LocalDate.of(2024, 3, 1), "4.800");
        seed("Brazil-Real", LocalDate.of(2024, 6, 1), "5.000");
        seed("Iran-Real", LocalDate.of(2024, 7, 1), "42.000");

        final var latest =
                repository.findLatestExactCountryCurrencyInRange("Brazil-Real", WINDOW.start(), WINDOW.end());

        assertTrue(latest.isPresent());
        assertEquals(LocalDate.of(2024, 6, 1), latest.get().getEffectiveDate());
        assertEquals(new BigDecimal("5.000"), latest.get().getRateValue());
    }

    @Test
    @DisplayName("a case-insensitive exact input resolves to exactly one candidate")
    void givenExactInput_whenResolving_thenExactlyOneCandidateIsFound() {
        seed("Brazil-Real", LocalDate.of(2024, 6, 1), "5.000");
        seed("Iran-Real", LocalDate.of(2024, 6, 1), "42.000");

        final var candidates =
                repository.findDistinctCountryCurrenciesInRange("brazil-real", WINDOW.start(), WINDOW.end());

        assertEquals(List.of("Brazil-Real"), candidates);
    }

    @Test
    @DisplayName("a rate outside the window is not a candidate and is not returned as the latest")
    void givenRateOutsideTheWindow_whenQuerying_thenItIsExcluded() {
        seed("Brazil-Real", LocalDate.of(2023, 1, 1), "5.000");

        assertTrue(repository.findDistinctCountryCurrenciesInRange("real", WINDOW.start(), WINDOW.end()).isEmpty());
        assertTrue(repository.findLatestExactCountryCurrencyInRange(
                "Brazil-Real", WINDOW.start(), WINDOW.end()).isEmpty());
    }

    @Test
    @DisplayName("a nonexistent currency resolves to no candidates")
    void givenNoStoredRate_whenResolving_thenThereAreNoCandidates() {
        assertTrue(repository.findDistinctCountryCurrenciesInRange("Nowhere-Coin", WINDOW.start(), WINDOW.end())
                .isEmpty());
    }

    private void seed(final String countryCurrency, final LocalDate effectiveDate, final String rateValue) {
        repository.save(ExchangeRateJpaEntity.newConversionRate(countryCurrency, effectiveDate, new BigDecimal(rateValue)));
    }
}
