package com.pedrolima.wexchange.api.controllers;

import com.pedrolima.wexchange.api.CountryCurrencyApi;
import com.pedrolima.wexchange.bean.exchange.CountryCurrencyOutput;
import com.pedrolima.wexchange.service.CurrenciesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CountryCurrencyController implements CountryCurrencyApi {

    private final CurrenciesService currenciesService;
    @Override
    public ResponseEntity<CountryCurrencyOutput> findAll() {
        return ResponseEntity.ok(currenciesService.getAllExchangeRates());
    }
}
