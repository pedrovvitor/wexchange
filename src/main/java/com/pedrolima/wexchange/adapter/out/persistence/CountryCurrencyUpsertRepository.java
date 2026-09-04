package com.pedrolima.wexchange.adapter.out.persistence;

import com.pedrolima.wexchange.application.port.CountryCurrencyRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Bulk-upserts country currencies (issue #6) in a single batched round trip,
 * replacing the old per-row exists-check-then-{@code saveAll} pattern - see
 * {@link ExchangeRateUpsertRepository} for the identical rationale, including
 * why {@code upsertAll} is {@code @Transactional} in its own right rather
 * than relying on a caller's transaction: {@code spring.datasource.hikari.auto-commit}
 * is {@code false}, so nothing commits this batch otherwise.
 */
@Repository
public class CountryCurrencyUpsertRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO country_currency (country_currency, country, currency)
            VALUES (?, ?, ?)
            ON CONFLICT (country_currency)
            DO UPDATE SET country = EXCLUDED.country, currency = EXCLUDED.currency
            WHERE country_currency.country <> EXCLUDED.country OR country_currency.currency <> EXCLUDED.currency
            """;

    private final JdbcTemplate jdbcTemplate;

    public CountryCurrencyUpsertRepository(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void upsertAll(final List<CountryCurrencyRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(UPSERT_SQL, records, records.size(), (ps, record) -> {
            ps.setString(1, record.countryCurrency());
            ps.setString(2, record.country());
            ps.setString(3, record.currency());
        });
    }
}
