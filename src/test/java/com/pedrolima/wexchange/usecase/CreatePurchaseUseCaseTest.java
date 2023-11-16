package com.pedrolima.wexchange.usecase;

import com.pedrolima.wexchange.api.purchase.CreatePurchaseApiInput;
import com.pedrolima.wexchange.api.purchase.CreatePurchaseApiOutput;
import com.pedrolima.wexchange.entities.PurchaseJpaEntity;
import com.pedrolima.wexchange.repositories.PurchaseRepository;
import com.pedrolima.wexchange.services.async.ExchangeRateService;
import com.pedrolima.wexchange.usecases.purchase.create.DefaultCreatePurchaseUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CreatePurchaseUseCaseTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private ExchangeRateService exchangeRateService;

    @InjectMocks
    private DefaultCreatePurchaseUseCase createPurchaseUseCase;

    @Test
    void givenValidFirstPurchaseOfTheDate_whenCreatePurchase_thenPersistPurchaseAndUpdateExchangeRatesAndReturnDetails() {
        final var description = "Test Purchase";
        final var date = LocalDate.now();
        final var amount = BigDecimal.valueOf(100.0);
        final var input = new CreatePurchaseApiInput(description, date, amount);

        final var expectedPurchase = new PurchaseJpaEntity(description, date, amount);
        when(purchaseRepository.save(any(PurchaseJpaEntity.class))).thenReturn(expectedPurchase);
        Mockito.doNothing().when(exchangeRateService).updateExchangeRates(expectedPurchase);
        when(purchaseRepository.countByDate(date)).thenReturn(1L);

        CreatePurchaseApiOutput result = createPurchaseUseCase.execute(input);

        verify(purchaseRepository).save(any(PurchaseJpaEntity.class));
        verify(exchangeRateService).updateExchangeRates(any(PurchaseJpaEntity.class));
        assertEquals(description, result.description());
        assertEquals(date.toString(), result.date());
        assertEquals(amount, result.amount());
        assertFalse(result.links().isEmpty());
    }

    @Test
    void givenNotFirstPurchaseOfTheDate_whenCreatePurchase_thenPersistPurchaseWithoutUpdatingExchangeRatesAndReturnDetails() {
        final var description = "Test Purchase";
        final var date = LocalDate.now();
        final var amount = BigDecimal.valueOf(100.0);
        final var input = new CreatePurchaseApiInput(description, date, amount);

        final var expectedPurchase = new PurchaseJpaEntity(description, date, amount);
        when(purchaseRepository.save(any(PurchaseJpaEntity.class))).thenReturn(expectedPurchase);
        when(purchaseRepository.countByDate(date)).thenReturn(2L);

        CreatePurchaseApiOutput result = createPurchaseUseCase.execute(input);

        verify(purchaseRepository).save(any(PurchaseJpaEntity.class));
        verify(exchangeRateService, never()).updateExchangeRates(any(PurchaseJpaEntity.class));
        assertEquals(description, result.description());
        assertEquals(date.toString(), result.date());
        assertEquals(amount, result.amount());
        assertFalse(result.links().isEmpty());
    }
}
