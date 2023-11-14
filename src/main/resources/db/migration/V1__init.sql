CREATE TABLE IF NOT EXISTS purchase
(
    id VARCHAR
(
    36
) PRIMARY KEY,
    description VARCHAR
(
    50
) NOT NULL,
    date DATE NOT NULL,
    amount NUMERIC NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
    );
