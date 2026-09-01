package com.pedrolima.wexchange.usecase.convert;

import com.pedrolima.wexchange.entities.ExchangeRateJpaEntity;
import com.pedrolima.wexchange.entities.PurchaseJpaEntity;
import com.pedrolima.wexchange.exceptions.ExchangeRateNotFoundException;
import com.pedrolima.wexchange.exceptions.MultipleCountryCurrenciesException;
import com.pedrolima.wexchange.exceptions.ResourceNotFoundException;
import com.pedrolima.wexchange.purchase.models.ConvertPurchaseApiInput;
import com.pedrolima.wexchange.purchase.models.ConvertPurchaseApiOutput;
import com.pedrolima.wexchange.repositories.ExchangeRateRepository;
import com.pedrolima.wexchange.repositories.PurchaseRepository;
import com.pedrolima.wexchange.services.async.ExchangeRateService;
import com.pedrolima.wexchange.usecases.purchase.convert.DefaultConvertPurchaseUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.pedrolima.wexchange.usecases.purchase.convert.DefaultConvertPurchaseUseCase.MAX_COUNTRY_CURRENCY_LENGTH;
import static com.pedrolima.wexchange.usecases.purchase.convert.DefaultConvertPurchaseUseCase.MIN_COUNTRY_CURRENCY_LENGTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ConvertPurchaseUseCaseTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private ExchangeRateService exchangeRateService;

    @InjectMocks
    private DefaultConvertPurchaseUseCase convertPurchaseUseCase;

    @Test
    void givenAnInvalidSmallCountryCurrencyParam_whenCallsExecute_thenThrowIllegalArgumentException() {
        final var input = new ConvertPurchaseApiInput("purchaseId", "ab");
        final var expectedExceptionMessage = "Country Currency must have between 3 and 100 characters";
        final var actualException =
                assertThrows(IllegalArgumentException.class, () -> convertPurchaseUseCase.execute(input));

        assertEquals(expectedExceptionMessage, actualException.getMessage());
        verify(purchaseRepository, never()).findById(anyString());
        verify(exchangeRateRepository, never())
                .findLatestRatesByCountryCurrencyAndDateRange(anyString(), any(), any());
    }

    @Test
    void givenAnInvalidLargeCountryCurrencyParam_whenCallsExecute_thenThrowIllegalArgumentException() {
        final var largeCountryCurrency = """
                                         Lorem ipsum dolor sit amet, consectetur adipiscing elit.
                                         Duis ultricies volutpat ligula. Fusce dignissim risus nec
                                         tortor viverra, quis imperdiet elit egestas. Nam facilisis
                                         pellentesque bibendum. Donec congue tristique eros elementum
                                         sollicitudin. Cras consectetur pretium malesuada. Maecenas augue.
                                         """;
        final var input = new ConvertPurchaseApiInput("purchaseId", largeCountryCurrency);
        final var expectedExceptionMessage = "Country Currency must have between 3 and 100 characters";
        final var actualException =
                assertThrows(IllegalArgumentException.class, () -> convertPurchaseUseCase.execute(input));

        assertEquals(expectedExceptionMessage, actualException.getMessage());
        verify(purchaseRepository, never()).findById(anyString());
        verify(exchangeRateRepository, never())
                .findLatestRatesByCountryCurrencyAndDateRange(anyString(), any(), any());
    }

    @Test
    void givenAnInvalidEmptyCountryCurrencyParam_whenCallsExecute_thenThrowIllegalArgumentException() {
        final var largeCountryCurrency = "";
        final var input = new ConvertPurchaseApiInput("purchaseId", largeCountryCurrency);
        final var expectedExceptionMessage = "Country Currency must have between 3 and 100 characters";
        final var actualException =
                assertThrows(IllegalArgumentException.class, () -> convertPurchaseUseCase.execute(input));

        assertEquals(expectedExceptionMessage, actualException.getMessage());
        verify(purchaseRepository, never()).findById(anyString());
        verify(exchangeRateRepository, never())
                .findLatestRatesByCountryCurrencyAndDateRange(anyString(), any(), any());
    }

    @Test
    void givenNonExistentId_whenCallsExecute_thenThrowResourceNotFoundException() {
        final var input = new ConvertPurchaseApiInput("non existent ID", "Brazil-Real");
        final var expectedExceptionMessage = "Purchase not found for id: non existent ID";
        when(purchaseRepository.findById(input.purchaseId())).thenReturn(Optional.empty());

        final var actualException =
                assertThrows(ResourceNotFoundException.class, () -> convertPurchaseUseCase.execute(input));

        assertEquals(expectedExceptionMessage, actualException.getMessage());
        verify(purchaseRepository, times(1)).findById(anyString());
        verify(exchangeRateRepository, never())
                .findLatestRatesByCountryCurrencyAndDateRange(anyString(), any(), any());
        verify(exchangeRateService, never()).updateExchangeRates(any(PurchaseJpaEntity.class));
    }

    @Test
    void givenNonExistentCountryCurrency_whenCallsExecute_thenThrowExchangeRateNotFoundException() {
        final var input = new ConvertPurchaseApiInput(UUID.randomUUID().toString(), "XPTO");
        final var expectedExceptionMessage = "Exchange rate not found for currency XPTO";
        final var aPurchase = PurchaseJpaEntity.newPurchase("random Description", LocalDate.now(), BigDecimal.valueOf(100));

        when(purchaseRepository.findById(input.purchaseId())).thenReturn(Optional.of(aPurchase));
        when(exchangeRateRepository.findLatestRatesByCountryCurrencyAndDateRange(
                anyString(),
                any(LocalDate.class),
                any(LocalDate.class))
        ).thenReturn(Collections.emptyList());
        doNothing().when(exchangeRateService).updateExchangeRates(any(PurchaseJpaEntity.class));

        final var actualException =
                assertThrows(ExchangeRateNotFoundException.class, () -> convertPurchaseUseCase.execute(input));

        assertEquals(expectedExceptionMessage, actualException.getMessage());
        verify(purchaseRepository, times(1)).findById(anyString());
        verify(exchangeRateRepository, times(1))
                .findLatestRatesByCountryCurrencyAndDateRange(anyString(), any(LocalDate.class), any(LocalDate.class));
        verify(exchangeRateService, times(1)).updateExchangeRates(any(PurchaseJpaEntity.class));
    }

    @Test
    void givenMultipleExchangeRatesFound_whenExecute_thenThrowMultipleCountryCurrenciesException() {
        final var input = new ConvertPurchaseApiInput(UUID.randomUUID().toString(), "Real");
        final var purchase = PurchaseJpaEntity.newPurchase("Test Purchase", LocalDate.now(), BigDecimal.valueOf(100));
        final var rate1 = ExchangeRateJpaEntity.newConversionRate(
                "Brazil-Real", LocalDate.of(2023, 12, 1), BigDecimal.valueOf(5.000));
        final var rate2 = ExchangeRateJpaEntity.newConversionRate(
                "Iran-Real", LocalDate.of(2023, 12, 1), BigDecimal.valueOf(42.000));

        when(purchaseRepository.findById(input.purchaseId())).thenReturn(Optional.of(purchase));
        when(exchangeRateRepository.findLatestRatesByCountryCurrencyAndDateRange(anyString(), any(), any()))
                .thenReturn(List.of(rate1, rate2));

        final var thrown = assertThrows(
                MultipleCountryCurrenciesException.class, () -> convertPurchaseUseCase.execute(input));

        // The message reaches the caller verbatim in the 409 body, so it is contract.
        assertEquals("2 Country currencies found containing Real it: Brazil-Real, Iran-Real", thrown.getMessage());
        verify(purchaseRepository, times(1)).findById(anyString());
        verify(exchangeRateRepository, times(1))
                .findLatestRatesByCountryCurrencyAndDateRange(anyString(), any(LocalDate.class), any(LocalDate.class));
        verify(exchangeRateService, never()).updateExchangeRates(any(PurchaseJpaEntity.class));
    }

    @Test
    void givenValidInput_whenCallsExecute_thenReturnConvertedPurchaseDetails() {
        final var aDescription = "Test Purchase";
        final var aDate = LocalDate.now();
        final var anAmount = BigDecimal.valueOf(150).setScale(2, RoundingMode.HALF_EVEN);
        final var purchase = PurchaseJpaEntity.newPurchase(aDescription, aDate, anAmount);

        final var aCountryCurrency = "Brazil-Real";
        final var anInput = ConvertPurchaseApiInput.with(purchase.getId(), aCountryCurrency);

        final var anEffectiveDate = LocalDate.of(2023, 9, 30);
        final var aConversionRate = BigDecimal.valueOf(5.255);

        final var conversionRate = ExchangeRateJpaEntity.newConversionRate(
                aCountryCurrency,
                anEffectiveDate,
                aConversionRate
        );

        final var expectedOutput = new ConvertPurchaseApiOutput(
                purchase.getId(),
                aDescription,
                aDate.toString(),
                aCountryCurrency,
                anAmount,
                aConversionRate,
                anEffectiveDate.toString(),
                anAmount.multiply(aConversionRate).setScale(2, RoundingMode.HALF_EVEN),
                Collections.emptyList()
        );

        when(purchaseRepository.findById(anInput.purchaseId())).thenReturn(Optional.of(purchase));
        when(exchangeRateRepository.findLatestRatesByCountryCurrencyAndDateRange(any(), any(), any()))
                .thenReturn(Collections.singletonList(conversionRate));

        ConvertPurchaseApiOutput actualOutput = convertPurchaseUseCase.execute(anInput);

        assertEquals(expectedOutput.id(), actualOutput.id());
        assertEquals(expectedOutput.description(), actualOutput.description());
        assertEquals(expectedOutput.transactionDate(), actualOutput.transactionDate());
        assertEquals(expectedOutput.conversionCountryCurrency(), actualOutput.conversionCountryCurrency());
        assertEquals(expectedOutput.originalAmount(), actualOutput.originalAmount());
        assertEquals(expectedOutput.rateValue(), actualOutput.rateValue());
        assertEquals(expectedOutput.rateEffectiveDate(), actualOutput.rateEffectiveDate());
        assertEquals(expectedOutput.convertedAmount(), actualOutput.convertedAmount());

        verify(purchaseRepository, times(1)).findById(anyString());
        verify(exchangeRateRepository, times(1))
                .findLatestRatesByCountryCurrencyAndDateRange(anyString(), any(LocalDate.class), any(LocalDate.class));
        verify(exchangeRateService, never()).updateExchangeRates(any(PurchaseJpaEntity.class));
    }

    @ParameterizedTest(name = "a {0}-character country-currency is accepted")
    @ValueSource(ints = {MIN_COUNTRY_CURRENCY_LENGTH, MAX_COUNTRY_CURRENCY_LENGTH})
    void givenCountryCurrencyAtTheLengthBoundary_whenCallsExecute_thenItIsAccepted(final int length) {
        final var countryCurrency = "C".repeat(length);
        final var purchase = PurchaseJpaEntity.newPurchase("Test Purchase", LocalDate.of(2024, 1, 31),
                BigDecimal.valueOf(150).setScale(2, RoundingMode.HALF_EVEN));
        final var input = ConvertPurchaseApiInput.with(purchase.getId(), countryCurrency);

        when(purchaseRepository.findById(input.purchaseId())).thenReturn(Optional.of(purchase));
        when(exchangeRateRepository.findLatestRatesByCountryCurrencyAndDateRange(any(), any(), any()))
                .thenReturn(Collections.singletonList(ExchangeRateJpaEntity.newConversionRate(
                        countryCurrency, LocalDate.of(2023, 12, 1), BigDecimal.valueOf(2.000))));

        final var output = convertPurchaseUseCase.execute(input);

        assertEquals(countryCurrency, output.conversionCountryCurrency());
    }

    @ParameterizedTest(name = "a {0}-character country-currency is rejected")
    @ValueSource(ints = {MIN_COUNTRY_CURRENCY_LENGTH - 1, MAX_COUNTRY_CURRENCY_LENGTH + 1})
    void givenCountryCurrencyJustOutsideTheLengthBoundary_whenCallsExecute_thenItIsRejected(final int length) {
        final var input = ConvertPurchaseApiInput.with("purchaseId", "C".repeat(length));

        final var thrown = assertThrows(IllegalArgumentException.class, () -> convertPurchaseUseCase.execute(input));

        assertEquals("Country Currency must have between 3 and 100 characters", thrown.getMessage());
        verify(purchaseRepository, never()).findById(anyString());
    }

    @Test
    void givenValidInput_whenCallsExecute_thenTheRateIsSelectedWithinTheSixMonthsBeforeThePurchase() {
        final var purchaseDate = LocalDate.of(2024, 7, 15);
        final var purchase = PurchaseJpaEntity.newPurchase("Test Purchase", purchaseDate,
                BigDecimal.valueOf(150).setScale(2, RoundingMode.HALF_EVEN));
        final var input = ConvertPurchaseApiInput.with(purchase.getId(), "Brazil-Real");

        when(purchaseRepository.findById(input.purchaseId())).thenReturn(Optional.of(purchase));
        when(exchangeRateRepository.findLatestRatesByCountryCurrencyAndDateRange(any(), any(), any()))
                .thenReturn(Collections.singletonList(ExchangeRateJpaEntity.newConversionRate(
                        "Brazil-Real", LocalDate.of(2024, 7, 1), BigDecimal.valueOf(5.000))));

        convertPurchaseUseCase.execute(input);

        final var currency = ArgumentCaptor.forClass(String.class);
        final var from = ArgumentCaptor.forClass(LocalDate.class);
        final var to = ArgumentCaptor.forClass(LocalDate.class);
        verify(exchangeRateRepository)
                .findLatestRatesByCountryCurrencyAndDateRange(currency.capture(), from.capture(), to.capture());

        assertEquals("Brazil-Real", currency.getValue());
        assertEquals(LocalDate.of(2024, 1, 15), from.getValue());
        assertEquals(purchaseDate, to.getValue());
    }

    @Test
    void givenValidInput_whenCallsExecute_thenTheResponseAdvertisesTheRelatedEndpoints() {
        final var purchase = PurchaseJpaEntity.newPurchase("Test Purchase", LocalDate.of(2024, 1, 31),
                BigDecimal.valueOf(150).setScale(2, RoundingMode.HALF_EVEN));
        final var input = ConvertPurchaseApiInput.with(purchase.getId(), "Brazil-Real");

        when(purchaseRepository.findById(input.purchaseId())).thenReturn(Optional.of(purchase));
        when(exchangeRateRepository.findLatestRatesByCountryCurrencyAndDateRange(any(), any(), any()))
                .thenReturn(Collections.singletonList(ExchangeRateJpaEntity.newConversionRate(
                        "Brazil-Real", LocalDate.of(2023, 12, 1), BigDecimal.valueOf(5.000))));

        final var links = convertPurchaseUseCase.execute(input).links();

        assertEquals(2, links.size());
        assertEquals("purchase", links.get(0).rel());
        assertEquals("/v1/purchases", links.get(0).href());
        assertEquals("POST", links.get(0).method());
        assertEquals("country_currencies", links.get(1).rel());
        assertEquals("/v1/country_currencies?country_currency=", links.get(1).href());
        assertEquals("GET", links.get(1).method());
        assertEquals(Set.of("country_currency"), links.get(1).params().keySet());
    }
}
