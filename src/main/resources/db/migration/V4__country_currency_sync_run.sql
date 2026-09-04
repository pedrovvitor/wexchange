-- Observability for the country-currency scheduled sync job (issue #6): a
-- single row tracking the most recent run, plus the last time it actually
-- succeeded and the last time it actually failed - independently of each
-- other, since a failed run must never erase when the last success was, and
-- vice versa.

CREATE TABLE country_currency_sync_run
(
    id                  VARCHAR(20)  NOT NULL PRIMARY KEY,
    status              VARCHAR(20)  NOT NULL,
    started_at          TIMESTAMP(6) NOT NULL,
    finished_at         TIMESTAMP(6),
    last_success_at     TIMESTAMP(6),
    last_failure_at     TIMESTAMP(6),
    last_error_message  VARCHAR(500)
);
