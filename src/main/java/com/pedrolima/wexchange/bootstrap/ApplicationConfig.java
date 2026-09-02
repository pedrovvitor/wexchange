package com.pedrolima.wexchange.bootstrap;

import com.pedrolima.wexchange.application.ConvertPurchaseService;
import com.pedrolima.wexchange.application.ConvertPurchaseUseCase;
import com.pedrolima.wexchange.application.CreatePurchaseService;
import com.pedrolima.wexchange.application.CreatePurchaseUseCase;
import com.pedrolima.wexchange.application.port.ExchangeRateRefresher;
import com.pedrolima.wexchange.application.port.ExchangeRateStore;
import com.pedrolima.wexchange.application.port.IdentifierGenerator;
import com.pedrolima.wexchange.application.port.PurchaseStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the use cases.
 *
 * <p>The application classes carry no Spring annotations, so their beans are
 * declared here instead. That is the whole cost of keeping the layer
 * framework-free, and it buys use cases that can be constructed in a test with
 * {@code new} and no container.
 */
@Configuration
public class ApplicationConfig {

    @Bean
    public CreatePurchaseUseCase createPurchaseUseCase(
            final PurchaseStore purchases,
            final ExchangeRateRefresher rateRefresher,
            final IdentifierGenerator identifiers,
            final Clock clock
    ) {
        return new CreatePurchaseService(purchases, rateRefresher, identifiers, clock);
    }

    @Bean
    public ConvertPurchaseUseCase convertPurchaseUseCase(
            final PurchaseStore purchases,
            final ExchangeRateStore rates,
            final ExchangeRateRefresher rateRefresher
    ) {
        return new ConvertPurchaseService(purchases, rates, rateRefresher);
    }
}
