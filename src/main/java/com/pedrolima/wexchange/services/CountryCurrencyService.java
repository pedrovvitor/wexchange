package com.pedrolima.wexchange.services;

import com.pedrolima.wexchange.api.ApiLink;
import com.pedrolima.wexchange.integration.fiscal.beans.CountryCurrency;
import com.pedrolima.wexchange.integration.fiscal.beans.CountryCurrencyOutput;
import com.pedrolima.wexchange.repositories.CountryCurrencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class CountryCurrencyService {

    private final CountryCurrencyRepository repository;

    public CountryCurrencyOutput findByCountryCurrency(final Pageable pageable, final String countryCurrency) {
        final Page<CountryCurrency> countryCurrencies;

        if (StringUtils.isBlank(countryCurrency)) {
            countryCurrencies = repository.findAll(pageable)
                    .map(CountryCurrency::with);
        } else {
            countryCurrencies = repository.findAllContainingCountryCurrencyIgnoreCase(pageable, countryCurrency)
                    .map(CountryCurrency::with);
        }
        final var convertParams = Map.of(
                "{id}", "String: Purchase id (UUID format)",
                "country_currency", "String: Country-Currency to convert"
        );
        final var relatedLinks = List.of(
                ApiLink.with("convert", "/v1/purchases/{id}/convert?country_currency=", "GET", convertParams)
        );

        return CountryCurrencyOutput.with(countryCurrencies, relatedLinks);
    }
}
