package com.pedrolima.wexchange.entities;

import com.pedrolima.wexchange.integration.fiscal.bean.ConversionRate;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "conversion_rate")
@IdClass(ConversionRateCompositeKey.class)
@Getter
public class ConversionRateJpaEntity {

    @Id
    private String countryCurrency;

    @Id
    private LocalDate effectiveDate;

    private BigDecimal exchangeRate;

    public ConversionRateJpaEntity() {
    }

    private ConversionRateJpaEntity(
            final String countryCurrency,
            final LocalDate effectiveDate,
            final BigDecimal exchangeRate
    ) {
        this.countryCurrency = countryCurrency;
        this.effectiveDate = effectiveDate;
        this.exchangeRate = exchangeRate;
    }

    public static ConversionRateJpaEntity with(
            final ConversionRate aConversionRate) {
        return new ConversionRateJpaEntity(
                aConversionRate.countryCurrency(),
                aConversionRate.effectiveDate(),
                aConversionRate.exchangeRate()
        );
    }
}
