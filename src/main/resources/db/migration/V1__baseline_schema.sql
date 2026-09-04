-- Baseline schema (issue #5). This is the one canonical schema history:
-- db-scripts/schema.sql (a Docker init script) is retired in the same change
-- that introduces this migration, so there is never a second source of truth
-- for the schema. Column types and precision are pinned exactly to what the
-- JPA entities under adapter.out.persistence declare, since
-- spring.jpa.hibernate.ddl-auto=validate checks the two against each other on
-- every startup.

CREATE TABLE purchase
(
    id            VARCHAR(36)     NOT NULL PRIMARY KEY,
    description   VARCHAR(50)     NOT NULL,
    purchase_date DATE            NOT NULL,
    amount        NUMERIC(14, 2)  NOT NULL,
    created_at    TIMESTAMP(6)    NOT NULL,
    updated_at    TIMESTAMP(6)    NOT NULL
);

CREATE TABLE country_currency
(
    country_currency VARCHAR(255) NOT NULL PRIMARY KEY,
    country          VARCHAR(255) NOT NULL,
    currency         VARCHAR(255) NOT NULL
);

CREATE TABLE exchange_rate
(
    country_currency VARCHAR(255)   NOT NULL,
    effective_date   DATE           NOT NULL,
    rate_value       NUMERIC(12, 3) NOT NULL,
    PRIMARY KEY (country_currency, effective_date)
);
