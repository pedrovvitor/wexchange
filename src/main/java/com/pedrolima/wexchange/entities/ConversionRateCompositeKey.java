package com.pedrolima.wexchange.entities;

import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Getter
public class ConversionRateCompositeKey implements Serializable {

    private String countryCurrency;
    private LocalDate effectiveDate;

    public ConversionRateCompositeKey() {

    }
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ConversionRateCompositeKey that = (ConversionRateCompositeKey) o;
        return Objects.equals(countryCurrency, that.countryCurrency) && Objects.equals(effectiveDate, that.effectiveDate);
    }
    @Override
    public int hashCode() {
        return Objects.hash(countryCurrency, effectiveDate);
    }
}
