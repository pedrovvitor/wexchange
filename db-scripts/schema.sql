create table if not exists purchase
(
    id VARCHAR
(
    36
) not null primary key,
    description VARCHAR
    (50) not null,
    purchase_date DATE not null,
    amount NUMERIC(14, 2) NOT NULL,
    created_at TIMESTAMP
    (6) not null,
    updated_at TIMESTAMP
    (6) not null
    );

create table if not exists country_currency
(
    country_currency VARCHAR
(
    255
) not null primary key,
    country VARCHAR
    (255) not null,
    currency VARCHAR
    (255) not null
    );

create table if not exists exchange_rate
(
    country_currency VARCHAR
(
    255
) not null,
    effective_date DATE not null,
    rate_value NUMERIC not null,
    primary key
    (country_currency, effective_date)
    );
