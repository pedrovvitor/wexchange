package com.pedrolima.wexchange.adapter.out.persistence;

import com.pedrolima.wexchange.domain.exchange.ConversionWindow;
import com.pedrolima.wexchange.domain.money.Money;
import com.pedrolima.wexchange.domain.purchase.Purchase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The store adapters exist to translate. These tests assert that the translation
 * happens in both directions and that nothing domain-shaped leaks into the
 * repository call, or entity-shaped back out of it.
 */
@ExtendWith(MockitoExtension.class)
class StoreAdapterTest {

    private static final String ID = "b3f81c25-7d40-4e69-9a12-58c7de034f6b";

    private static final LocalDate DATE = LocalDate.of(2024, 7, 15);

    private static final Instant NOW = Instant.parse("2024-07-15T12:00:00Z");

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @InjectMocks
    private PurchaseStoreAdapter purchases;

    @InjectMocks
    private ExchangeRateStoreAdapter rates;

    private static Purchase aPurchase() {
        return Purchase.restore(ID, "A purchase", DATE, Money.of("10.00"), NOW, NOW);
    }

    @Test
    @DisplayName("saving passes an entity down and returns a domain purchase back")
    void givenPurchase_whenSaving_thenItIsTranslatedBothWays() {
        when(purchaseRepository.save(any(PurchaseJpaEntity.class))).thenAnswer(call -> call.getArgument(0));

        final var saved = purchases.save(aPurchase());

        final var persisted = ArgumentCaptor.forClass(PurchaseJpaEntity.class);
        verify(purchaseRepository).save(persisted.capture());
        assertEquals(ID, persisted.getValue().getId());
        assertEquals(new BigDecimal("10.00"), persisted.getValue().getAmount());
        assertEquals(aPurchase(), saved);
    }

    @Test
    @DisplayName("a found row becomes a domain purchase")
    void givenStoredRow_whenFindingById_thenADomainPurchaseIsReturned() {
        when(purchaseRepository.findById(ID)).thenReturn(Optional.of(
                PurchaseJpaEntity.newPurchase(ID, "A purchase", DATE, new BigDecimal("10.00"), NOW)));

        assertEquals(Optional.of(aPurchase()), purchases.findById(ID));
    }

    @Test
    @DisplayName("a missing row is an empty optional, not a null purchase")
    void givenNoRow_whenFindingById_thenTheResultIsEmpty() {
        when(purchaseRepository.findById(ID)).thenReturn(Optional.empty());

        assertTrue(purchases.findById(ID).isEmpty());
    }

    @Test
    @DisplayName("counting delegates without translation")
    void givenDate_whenCounting_thenTheRepositoryCountIsReturned() {
        when(purchaseRepository.countByPurchaseDate(DATE)).thenReturn(3L);

        assertEquals(3L, purchases.countByPurchaseDate(DATE));
    }

    @Test
    @DisplayName("resolution unpacks the window into the repository's date range")
    void givenWindow_whenResolvingCandidates_thenItsBoundsAreUsed() {
        final var window = ConversionWindow.endingOn(DATE);
        when(exchangeRateRepository.findDistinctCountryCurrenciesInRange(
                "Real", window.start(), window.end()))
                .thenReturn(List.of("Brazil-Real", "Iran-Real"));

        assertEquals(List.of("Brazil-Real", "Iran-Real"), rates.resolveCandidates("Real", window));
    }

    @Test
    @DisplayName("no matching currency resolves to an empty list, not null")
    void givenNoMatch_whenResolvingCandidates_thenTheListIsEmpty() {
        final var window = ConversionWindow.endingOn(DATE);
        when(exchangeRateRepository.findDistinctCountryCurrenciesInRange(any(), any(), any()))
                .thenReturn(List.of());

        assertTrue(rates.resolveCandidates("Real", window).isEmpty());
    }

    @Test
    @DisplayName("the exact lookup translates the found row into a domain rate")
    void givenExactCurrency_whenFindingLatest_thenADomainRateIsReturned() {
        final var window = ConversionWindow.endingOn(DATE);
        when(exchangeRateRepository.findLatestExactCountryCurrencyInRange(
                "Brazil-Real", window.start(), window.end()))
                .thenReturn(Optional.of(ExchangeRateJpaEntity.newConversionRate(
                        "Brazil-Real", LocalDate.of(2024, 7, 1), new BigDecimal("5.000"))));

        final var found = rates.findLatestExact("Brazil-Real", window);

        assertTrue(found.isPresent());
        assertEquals("Brazil-Real", found.get().countryCurrency());
        assertEquals(new BigDecimal("5.000"), found.get().rateValue());
        assertEquals(LocalDate.of(2024, 7, 1), found.get().effectiveDate());
    }

    @Test
    @DisplayName("no exact row is an empty optional, not a null rate")
    void givenNoExactRow_whenFindingLatest_thenTheResultIsEmpty() {
        final var window = ConversionWindow.endingOn(DATE);
        when(exchangeRateRepository.findLatestExactCountryCurrencyInRange(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertTrue(rates.findLatestExact("Brazil-Real", window).isEmpty());
    }
}
