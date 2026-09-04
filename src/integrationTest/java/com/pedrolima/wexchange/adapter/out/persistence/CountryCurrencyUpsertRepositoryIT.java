package com.pedrolima.wexchange.adapter.out.persistence;

import com.pedrolima.wexchange.application.port.CountryCurrencyRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link CountryCurrencyUpsertRepository} against a real PostgreSQL (issue
 * #6) - see {@link ExchangeRateUpsertRepositoryIT} for the identical
 * rationale; this class only needs to prove the same {@code ON CONFLICT}
 * shape works for this table too, not repeat every scenario.
 */
@Import(CountryCurrencyUpsertRepository.class)
class CountryCurrencyUpsertRepositoryIT extends AbstractPostgresRepositoryIT {

    @Autowired
    private CountryCurrencyUpsertRepository upsertRepository;

    @Autowired
    private CountryCurrencyRepository countryCurrencyRepository;

    @Test
    void givenANewCountryCurrency_whenUpserting_thenItIsPersisted() {
        upsertRepository.upsertAll(List.of(new CountryCurrencyRecord("Brazil-Real", "Brazil", "Real")));

        final var stored = countryCurrencyRepository.findById("Brazil-Real").orElseThrow();
        assertEquals("Brazil", stored.getCountry());
        assertEquals("Real", stored.getCurrency());
    }

    @Test
    void givenAnExistingCountryCurrencyWithCorrectedNames_whenUpserting_thenTheStoredNamesAreUpdated() {
        upsertRepository.upsertAll(List.of(new CountryCurrencyRecord("Correction-Coin", "Old Name", "Old Currency")));

        upsertRepository.upsertAll(List.of(new CountryCurrencyRecord("Correction-Coin", "New Name", "New Currency")));

        final var stored = countryCurrencyRepository.findById("Correction-Coin").orElseThrow();
        assertEquals("New Name", stored.getCountry());
        assertEquals("New Currency", stored.getCurrency());
    }

    @Test
    void givenAnEmptyBatch_whenUpserting_thenItIsANoOp() {
        upsertRepository.upsertAll(List.of());
    }
}
