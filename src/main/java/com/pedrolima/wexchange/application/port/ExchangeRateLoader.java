package com.pedrolima.wexchange.application.port;

import com.pedrolima.wexchange.domain.exchange.ConversionWindow;

/**
 * A bounded, synchronous, per-currency load-through for a conversion request
 * that just missed the local cache (issue #4). Unlike
 * {@link ExchangeRateRefresher}'s fire-and-forget warm-up, a caller here waits
 * for the outcome, and concurrent misses for the identical currency and
 * window are coalesced into one upstream call rather than each starting their
 * own - a request storm for one missing currency does not become an upstream
 * request storm.
 *
 * <p>Returns normally whether or not the provider actually had a matching
 * rate - the caller re-queries {@link ExchangeRateStore} afterward to find
 * out, which is what lets a genuinely absent rate still resolve to {@code 404}
 * rather than something this port invents a different shape for. Throws only
 * when the upstream call itself failed or exhausted its own retries
 * ({@link com.pedrolima.wexchange.domain.error.RetryableException}), which the
 * caller lets propagate as a truthful {@code 503} instead of a false
 * {@code 404}.
 */
public interface ExchangeRateLoader {

    void loadExact(String countryCurrency, ConversionWindow window);
}
