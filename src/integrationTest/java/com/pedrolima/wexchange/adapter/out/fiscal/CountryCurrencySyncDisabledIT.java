package com.pedrolima.wexchange.adapter.out.fiscal;

import com.pedrolima.wexchange.adapter.in.web.AbstractPostgresApplicationIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@code app.country-currency-sync.enabled=false} - set for every
 * {@link AbstractPostgresApplicationIT}-based suite - removes
 * {@link CountryCurrencyUpdaterService} from the context entirely (issue #6):
 * an early-return guard inside the method would still register the
 * {@code @Scheduled} trigger and still leave the bean present for this
 * assertion to find; {@code @ConditionalOnProperty} on the class does not.
 */
class CountryCurrencySyncDisabledIT extends AbstractPostgresApplicationIT {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void givenSyncIsDisabled_whenInspectingTheContext_thenNoSchedulerBeanExists() {
        assertTrue(applicationContext.getBeansOfType(CountryCurrencyUpdaterService.class).isEmpty(),
                "CountryCurrencyUpdaterService must not exist in the context when app.country-currency-sync.enabled=false");
    }
}
