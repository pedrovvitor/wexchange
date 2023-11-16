package com.pedrolima.wexchange.entities;

import com.pedrolima.wexchange.integration.fiscal.bean.ConversionRate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConversionRateJpaEntityTest {

    @Test
    void givenValidConversionRate_whenCallWith_thenInstantiateConversionRateJpaEntity() {
        final var countryCurrency = "Brazil-Real";
        final var effectiveDate = LocalDate.now();
        final var exchangeRate = BigDecimal.valueOf(1.2);

        ConversionRateJpaEntity conversionRateJpaEntity = ConversionRateJpaEntity.with(
                new ConversionRate(exchangeRate, effectiveDate, countryCurrency));

        assertEquals(countryCurrency, conversionRateJpaEntity.getCountryCurrency());
        assertEquals(effectiveDate, conversionRateJpaEntity.getEffectiveDate());
        assertEquals(exchangeRate, conversionRateJpaEntity.getExchangeRate());
    }
}
