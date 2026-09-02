package com.pedrolima.wexchange.adapter.out.persistence;

import com.pedrolima.wexchange.application.port.ExchangeRateStore;
import com.pedrolima.wexchange.domain.exchange.ConversionWindow;
import com.pedrolima.wexchange.domain.exchange.ExchangeRate;
import org.springframework.stereotype.Component;

import java.util.List;

/** Backs {@link ExchangeRateStore} with Spring Data, translating at the boundary. */
@Component
public class ExchangeRateStoreAdapter implements ExchangeRateStore {

    private final ExchangeRateRepository repository;

    public ExchangeRateStoreAdapter(final ExchangeRateRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ExchangeRate> findLatestWithin(final String countryCurrency, final ConversionWindow window) {
        return repository
                .findLatestRatesByCountryCurrencyAndDateRange(countryCurrency, window.start(), window.end())
                .stream()
                .map(ExchangeRateJpaEntity::toDomain)
                .toList();
    }
}
