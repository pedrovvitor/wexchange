CREATE TABLE IF NOT EXISTS purchase (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    description VARCHAR(50) NOT NULL,
    date DATE NOT NULL,
    amount NUMERIC NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
    );

CREATE TABLE IF NOT EXISTS country_currency (
    countryCurrency VARCHAR(255) NOT NULL PRIMARY KEY,
    country VARCHAR(255) NOT NULL,
    currency VARCHAR(255) NOT NULL
    );

CREATE TABLE IF NOT EXISTS conversion_rate (
    countryCurrency VARCHAR(255) NOT NULL,
    effectiveDate DATE NOT NULL,
    exchangeRate NUMERIC NOT NULL,
    PRIMARY KEY (countryCurrency, effectiveDate)
    );
