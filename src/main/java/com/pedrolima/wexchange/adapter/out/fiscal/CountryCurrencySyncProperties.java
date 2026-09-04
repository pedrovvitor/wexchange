package com.pedrolima.wexchange.adapter.out.fiscal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Enablement, cron expression, and timezone for the country-currency
 * scheduled sync (issue #6) - previously a hardcoded
 * {@code fixedRate}/{@code initialDelay} pair with no way to disable it (an
 * automated test suite that boots the real application had no way to opt out
 * either, which is exactly the noisy, always-on-network-call behaviour this
 * record's {@code enabled} flag exists to let tests turn off).
 */
@ConfigurationProperties(prefix = "app.country-currency-sync")
public record CountryCurrencySyncProperties(boolean enabled, String cron, String zone) {

    public CountryCurrencySyncProperties {
        cron = (cron != null && !cron.isBlank()) ? cron : "0 0 2 * * *";
        zone = (zone != null && !zone.isBlank()) ? zone : "UTC";
    }
}
