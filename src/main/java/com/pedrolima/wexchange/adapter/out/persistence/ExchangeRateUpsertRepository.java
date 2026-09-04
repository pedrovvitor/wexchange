package com.pedrolima.wexchange.adapter.out.persistence;

import com.pedrolima.wexchange.application.port.ExchangeRateQuote;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Bulk-upserts exchange rates (issue #6) in a single batched round trip,
 * replacing the old per-row exists-check-then-{@code saveAll} pattern: that
 * was both an N+1 query and a check-then-insert race under concurrent
 * refreshes for the same currency and window. {@code ON CONFLICT} makes the
 * write itself the concurrency defense - two replicas upserting the same
 * (country_currency, effective_date) row at the same instant both succeed,
 * deterministically converging on whichever value the database serializes
 * last - and doubles as the correction rule for a value the provider revises
 * later: the newest fetch always wins over what is currently stored.
 *
 * <p>{@code upsertAll} is {@code @Transactional} in its own right, not merely
 * for consistency: {@code spring.datasource.hikari.auto-commit} is {@code false}
 * for this application, so a connection {@link JdbcTemplate} obtains with no
 * Spring-managed transaction already active never commits anything it writes
 * - the batch would silently vanish the moment the connection is returned to
 * the pool. Declaring the transaction here means this method commits its own
 * work whether or not a caller happens to already be inside one.
 */
@Repository
public class ExchangeRateUpsertRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO exchange_rate (country_currency, effective_date, rate_value)
            VALUES (?, ?, ?)
            ON CONFLICT (country_currency, effective_date)
            DO UPDATE SET rate_value = EXCLUDED.rate_value
            WHERE exchange_rate.rate_value <> EXCLUDED.rate_value
            """;

    private final JdbcTemplate jdbcTemplate;

    public ExchangeRateUpsertRepository(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void upsertAll(final List<ExchangeRateQuote> quotes) {
        if (quotes.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(UPSERT_SQL, quotes, quotes.size(), (ps, quote) -> {
            ps.setString(1, quote.countryCurrency());
            ps.setObject(2, quote.effectiveDate());
            ps.setBigDecimal(3, quote.exchangeRate());
        });
    }
}
