package com.pedrolima.wexchange.application;

import com.pedrolima.wexchange.application.port.ExchangeRateLoader;
import com.pedrolima.wexchange.application.port.ExchangeRateStore;
import com.pedrolima.wexchange.application.port.PurchaseStore;
import com.pedrolima.wexchange.domain.error.ExchangeRateNotFoundException;
import com.pedrolima.wexchange.domain.error.MultipleCountryCurrenciesException;
import com.pedrolima.wexchange.domain.error.ResourceNotFoundException;
import com.pedrolima.wexchange.domain.error.RetryableException;
import com.pedrolima.wexchange.domain.exchange.ConversionWindow;
import com.pedrolima.wexchange.domain.exchange.ExchangeRate;
import com.pedrolima.wexchange.domain.money.Money;
import com.pedrolima.wexchange.domain.purchase.Purchase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.pedrolima.wexchange.application.ConvertPurchaseService.MAX_COUNTRY_CURRENCY_LENGTH;
import static com.pedrolima.wexchange.application.ConvertPurchaseService.MIN_COUNTRY_CURRENCY_LENGTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rate selection is a critical financial rule, so this class is a
 * mutation-testing target alongside the domain value types.
 *
 * <p>Resolution ({@code resolveCandidates}) and rate lookup ({@code findLatestExact})
 * are mocked as two separate steps because that separation is the fix for issue
 * #2: ambiguity is decided purely by how many distinct currencies match the
 * term, never by which one happens to have the most recent rate.
 */
@ExtendWith(MockitoExtension.class)
class ConvertPurchaseServiceTest {

    private static final String ID = "6e2b8a5d-3f17-4c90-a4e6-70d5c1b8f293";

    private static final LocalDate DATE = LocalDate.of(2024, 7, 15);

    private static final Instant NOW = Instant.parse("2024-07-15T12:00:00Z");

    @Mock
    private PurchaseStore purchases;

    @Mock
    private ExchangeRateStore rates;

    @Mock
    private ExchangeRateLoader rateLoader;

    private ConvertPurchaseService service;

    @BeforeEach
    void setUp() {
        service = new ConvertPurchaseService(purchases, rates, rateLoader);
    }

    private static Purchase aPurchase() {
        return Purchase.create(ID, "A purchase", DATE, Money.of("150.00"), NOW);
    }

    private static ExchangeRate aRate(final String countryCurrency, final String value) {
        return new ExchangeRate(countryCurrency, LocalDate.of(2024, 7, 1), new BigDecimal(value));
    }

    @Test
    @DisplayName("converts at the rate found for the resolved currency")
    void givenSingleCandidate_whenConverting_thenPurchaseIsConverted() {
        when(purchases.findById(ID)).thenReturn(Optional.of(aPurchase()));
        when(rates.resolveCandidates(any(), any())).thenReturn(List.of("Brazil-Real"));
        when(rates.findLatestExact("Brazil-Real", aPurchase().conversionWindow()))
                .thenReturn(Optional.of(aRate("Brazil-Real", "5.000")));

        final var converted = service.execute(ID, "Brazil-Real");

        assertEquals(Money.of("750.00"), converted.convertedAmount());
        assertEquals("Brazil-Real", converted.rate().countryCurrency());
    }

    @Test
    @DisplayName("resolution and the exact lookup both use the six months before the purchase")
    void givenPurchase_whenConverting_thenBothStepsUseTheSameWindow() {
        when(purchases.findById(ID)).thenReturn(Optional.of(aPurchase()));
        when(rates.resolveCandidates(any(), any())).thenReturn(List.of("Brazil-Real"));
        when(rates.findLatestExact(any(), any())).thenReturn(Optional.of(aRate("Brazil-Real", "5.000")));

        service.execute(ID, "Brazil-Real");

        final var resolveTerm = ArgumentCaptor.forClass(String.class);
        final var resolveWindow = ArgumentCaptor.forClass(ConversionWindow.class);
        verify(rates).resolveCandidates(resolveTerm.capture(), resolveWindow.capture());
        assertEquals("Brazil-Real", resolveTerm.getValue());
        assertEquals(LocalDate.of(2024, 1, 15), resolveWindow.getValue().start());
        assertEquals(DATE, resolveWindow.getValue().end());

        final var lookupCurrency = ArgumentCaptor.forClass(String.class);
        final var lookupWindow = ArgumentCaptor.forClass(ConversionWindow.class);
        verify(rates).findLatestExact(lookupCurrency.capture(), lookupWindow.capture());
        assertEquals("Brazil-Real", lookupCurrency.getValue());
        assertEquals(resolveWindow.getValue(), lookupWindow.getValue());
    }

    @Test
    @DisplayName("an unknown purchase is reported, and nothing is resolved")
    void givenUnknownPurchase_whenConverting_thenItIsReported() {
        when(purchases.findById(ID)).thenReturn(Optional.empty());

        final var thrown = assertThrows(ResourceNotFoundException.class, () -> service.execute(ID, "Brazil-Real"));

        assertEquals("Purchase not found for id: " + ID, thrown.getMessage());
        verify(rates, never()).resolveCandidates(anyString(), any());
    }

    @Test
    @DisplayName("no local candidate loads through synchronously, and a genuinely absent rate is still reported as 404")
    void givenNoCandidate_whenLoadThroughStillFindsNothing_thenTheGapIsReported() {
        when(purchases.findById(ID)).thenReturn(Optional.of(aPurchase()));
        when(rates.resolveCandidates(any(), any())).thenReturn(List.of());

        final var thrown =
                assertThrows(ExchangeRateNotFoundException.class, () -> service.execute(ID, "Brazil-Real"));

        assertEquals("Exchange rate not found for currency Brazil-Real", thrown.getMessage());
        verify(rateLoader).loadExact("Brazil-Real", aPurchase().conversionWindow());
        verify(rates, times(2)).resolveCandidates("Brazil-Real", aPurchase().conversionWindow());
        verify(rates, never()).findLatestExact(anyString(), any());
    }

    @Test
    @DisplayName("no local candidate but a valid upstream rate converts on the first request, not a retry")
    void givenNoCandidate_whenLoadThroughFindsTheRate_thenTheFirstRequestSucceeds() {
        when(purchases.findById(ID)).thenReturn(Optional.of(aPurchase()));
        when(rates.resolveCandidates(any(), any()))
                .thenReturn(List.of())
                .thenReturn(List.of("Brazil-Real"));
        when(rates.findLatestExact("Brazil-Real", aPurchase().conversionWindow()))
                .thenReturn(Optional.of(aRate("Brazil-Real", "5.000")));

        final var converted = service.execute(ID, "Brazil-Real");

        assertEquals(Money.of("750.00"), converted.convertedAmount());
        verify(rateLoader).loadExact("Brazil-Real", aPurchase().conversionWindow());
        verify(rates, times(2)).resolveCandidates("Brazil-Real", aPurchase().conversionWindow());
    }

    @Test
    @DisplayName("an upstream failure during load-through is a truthful 503, not a false 404")
    void givenNoCandidate_whenLoadThroughFails_thenTheFailurePropagatesRatherThanBecomingNotFound() {
        when(purchases.findById(ID)).thenReturn(Optional.of(aPurchase()));
        when(rates.resolveCandidates(any(), any())).thenReturn(List.of());
        doThrow(new RetryableException("Fiscal data API is unavailable"))
                .when(rateLoader).loadExact(anyString(), any());

        assertThrows(RetryableException.class, () -> service.execute(ID, "Brazil-Real"));

        verify(rates, times(1)).resolveCandidates(anyString(), any());
        verify(rates, never()).findLatestExact(anyString(), any());
    }

    @Test
    @DisplayName("two candidates are rejected as ambiguous, regardless of which has the newer rate")
    void givenTwoCandidates_whenConverting_thenAmbiguityIsReportedWithoutPickingOne() {
        when(purchases.findById(ID)).thenReturn(Optional.of(aPurchase()));
        when(rates.resolveCandidates(any(), any())).thenReturn(List.of("Brazil-Real", "Iran-Real"));

        final var thrown =
                assertThrows(MultipleCountryCurrenciesException.class, () -> service.execute(ID, "Real"));

        // The message reaches the caller verbatim in the 409 body, so it is contract.
        assertEquals("2 Country currencies found containing Real it: Brazil-Real, Iran-Real", thrown.getMessage());
        verify(rates, never()).findLatestExact(anyString(), any());
    }

    @Test
    @DisplayName("a resolved candidate with no rate at lookup time is still a 404, not a 500")
    void givenCandidateVanishesBeforeLookup_whenConverting_thenItIsReportedAsNotFound() {
        when(purchases.findById(ID)).thenReturn(Optional.of(aPurchase()));
        when(rates.resolveCandidates(any(), any())).thenReturn(List.of("Brazil-Real"));
        when(rates.findLatestExact(any(), any())).thenReturn(Optional.empty());

        final var thrown =
                assertThrows(ExchangeRateNotFoundException.class, () -> service.execute(ID, "Brazil-Real"));

        assertEquals("Exchange rate not found for currency Brazil-Real", thrown.getMessage());
    }

    @ParameterizedTest
    @DisplayName("a blank country-currency is rejected before anything is loaded")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void givenBlankTerm_whenConverting_thenItIsRejected(final String blank) {
        final var thrown = assertThrows(IllegalArgumentException.class, () -> service.execute(ID, blank));

        assertEquals("Country Currency must have between 3 and 100 characters", thrown.getMessage());
        verify(purchases, never()).findById(anyString());
    }

    @ParameterizedTest(name = "a {0}-character term is accepted")
    @ValueSource(ints = {MIN_COUNTRY_CURRENCY_LENGTH, MAX_COUNTRY_CURRENCY_LENGTH})
    void givenTermAtTheLengthBoundary_whenConverting_thenItIsAccepted(final int length) {
        final var term = "C".repeat(length);
        when(purchases.findById(ID)).thenReturn(Optional.of(aPurchase()));
        when(rates.resolveCandidates(any(), any())).thenReturn(List.of(term));
        when(rates.findLatestExact(any(), any())).thenReturn(Optional.of(aRate(term, "2.000")));

        assertEquals(term, service.execute(ID, term).rate().countryCurrency());
    }

    @ParameterizedTest(name = "a {0}-character term is rejected")
    @ValueSource(ints = {MIN_COUNTRY_CURRENCY_LENGTH - 1, MAX_COUNTRY_CURRENCY_LENGTH + 1})
    void givenTermOutsideTheLengthBoundary_whenConverting_thenItIsRejected(final int length) {
        assertThrows(IllegalArgumentException.class, () -> service.execute(ID, "C".repeat(length)));

        verify(purchases, never()).findById(anyString());
    }
}
