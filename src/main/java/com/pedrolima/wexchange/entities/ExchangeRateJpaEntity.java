package com.pedrolima.wexchange.entities;

import com.pedrolima.wexchange.integration.fiscal.beans.ConversionRate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "exchange_rate")
@IdClass(ExchangeRateCompositeKey.class)
@Getter
public class ExchangeRateJpaEntity {

    @Id
    @Column(name = "country_currency")
    private String countryCurrency;

    @Id
    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "rate_value", precision = 12, scale = 3)
    private BigDecimal rateValue;

    public ExchangeRateJpaEntity() {
    }

    private ExchangeRateJpaEntity(
            final String countryCurrency,
            final LocalDate effectiveDate,
            final BigDecimal rateValue
    ) {
        this.countryCurrency = countryCurrency;
        this.effectiveDate = effectiveDate;
        this.rateValue = rateValue;
    }

    public static ExchangeRateJpaEntity with(
            final ConversionRate aConversionRate) {
        return new ExchangeRateJpaEntity(
                aConversionRate.countryCurrency(),
                aConversionRate.effectiveDate(),
                aConversionRate.exchangeRate()
        );
    }

    public static ExchangeRateJpaEntity newConversionRate(
            final String aCountryCurrency,
            final LocalDate anEffectiveDate,
            final BigDecimal anExchangeRate
    ) {
        return new ExchangeRateJpaEntity(
                aCountryCurrency,
                anEffectiveDate,
                anExchangeRate
        );
    }
}
