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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
        final var input = new ConvertPurchaseApiInput(UUID.randomUUID().toString(), "Brazil-Real");
        final var purchase = new PurchaseJpaEntity("Test Purchase", LocalDate.now(), BigDecimal.valueOf(100));
        final var rate1 = new ExchangeRateJpaEntity();
        final var rate2 = new ExchangeRateJpaEntity();

        when(purchaseRepository.findById(input.purchaseId())).thenReturn(Optional.of(purchase));
        when(exchangeRateRepository.findLatestRatesByCountryCurrencyAndDateRange(anyString(), any(), any()))
                .thenReturn(List.of(rate1, rate2));

        assertThrows(MultipleCountryCurrenciesException.class, () -> convertPurchaseUseCase.execute(input));
        verify(purchaseRepository, times(1)).findById(anyString());
        verify(exchangeRateRepository, times(1))
                .findLatestRatesByCountryCurrencyAndDateRange(anyString(), any(LocalDate.class), any(LocalDate.class));
        verify(exchangeRateService, never()).updateExchangeRates(any(PurchaseJpaEntity.class));
    }

    @Test
    void givenValidInput_whenCallsExecute_thenReturnConvertedPurchaseDetails() {
        final var aDescription = "Test Purchase";
        final var aDate = LocalDate.now();
        final var anAmount = BigDecimal.valueOf(150);
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
                anAmount.multiply(aConversionRate).setScale(2, RoundingMode.HALF_UP),
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
}

