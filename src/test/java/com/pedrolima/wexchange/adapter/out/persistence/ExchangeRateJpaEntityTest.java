package com.pedrolima.wexchange.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExchangeRateJpaEntityTest {

    @Test
    void givenValidConversionRate_whenCallWith_thenInstantiateConversionRateJpaEntity() {
        final var countryCurrency = "Brazil-Real";
        final var effectiveDate = LocalDate.of(2024, 1, 31);
        final var exchangeRate = BigDecimal.valueOf(1.2);

        ExchangeRateJpaEntity exchangeRateJpaEntity =
                ExchangeRateJpaEntity.newConversionRate(countryCurrency, effectiveDate, exchangeRate);

        assertEquals(countryCurrency, exchangeRateJpaEntity.getCountryCurrency());
        assertEquals(effectiveDate, exchangeRateJpaEntity.getEffectiveDate());
        assertEquals(exchangeRate, exchangeRateJpaEntity.getRateValue());
    }
}
