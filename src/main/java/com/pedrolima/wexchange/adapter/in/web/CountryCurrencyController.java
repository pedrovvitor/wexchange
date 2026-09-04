package com.pedrolima.wexchange.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CountryCurrencyController implements CountryCurrencyApi {

    private final CountryCurrencyService countryCurrencyService;

    @Override
    public ResponseEntity<CountryCurrencyOutput> findByCountryCurrency(final Pageable pageable, final String countryCurrency) {
        return ResponseEntity.ok(countryCurrencyService.findByCountryCurrency(pageable, countryCurrency));
    }
}
