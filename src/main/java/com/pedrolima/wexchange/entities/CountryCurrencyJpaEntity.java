package com.pedrolima.wexchange.entities;

import com.pedrolima.wexchange.integration.fiscal.bean.CountryCurrency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.Objects;

@Entity
@Table(name = "country_currency")
@Getter
public class CountryCurrencyJpaEntity {

    @Id
    @Column(name = "country_currency")
    private String countryCurrency;

    @Column(name = "country")
    private String country;

    @Column(name = "currency")
    private String currency;

    public CountryCurrencyJpaEntity() {
    }

    private CountryCurrencyJpaEntity(final String countryCurrency, final String country, final String currency) {
        this.countryCurrency = countryCurrency;
        this.country = country;
        this.currency = currency;
    }

    public static CountryCurrencyJpaEntity with(CountryCurrency apiCountryCurrency) {
        return new CountryCurrencyJpaEntity(
                apiCountryCurrency.countryCurrency(),
                apiCountryCurrency.country(),
                apiCountryCurrency.currency());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final CountryCurrencyJpaEntity that = (CountryCurrencyJpaEntity) o;
        return Objects.equals(countryCurrency, that.countryCurrency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(countryCurrency);
    }
}
