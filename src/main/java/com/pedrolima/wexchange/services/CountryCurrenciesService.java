package com.pedrolima.wexchange.services;

import com.pedrolima.wexchange.api.ApiLink;
import com.pedrolima.wexchange.bean.exchange.CountryCurrencyOutput;
import com.pedrolima.wexchange.integration.fiscal.bean.CountryCurrency;
import com.pedrolima.wexchange.repositories.CountryCurrencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CountryCurrenciesService {

    private final CountryCurrencyRepository repository;

    public CountryCurrencyOutput findAllCountryCurrencies(final String countryCurrency) {
        final List<CountryCurrency> countryCurrencies;

        if (StringUtils.isBlank(countryCurrency)) {
            countryCurrencies = repository.findAll().stream()
                    .map(CountryCurrency::with)
                    .collect(Collectors.toList());
        } else {
            countryCurrencies = repository.findAllContainingIgnoreCase(countryCurrency).stream()
                    .map(CountryCurrency::with)
                    .collect(Collectors.toList());
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
