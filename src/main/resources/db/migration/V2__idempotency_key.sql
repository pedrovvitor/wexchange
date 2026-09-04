-- Purchase-creation idempotency claims (issue #18). Column types are pinned
-- exactly to what IdempotencyKeyJpaEntity declares, per V1's own rule:
-- spring.jpa.hibernate.ddl-auto=validate checks the two against each other on
-- every startup.

CREATE TABLE idempotency_key
(
    idempotency_key VARCHAR(255) NOT NULL PRIMARY KEY,
    fingerprint     VARCHAR(64)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    resource_id     VARCHAR(36),
    created_at      TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6) NOT NULL,
    expires_at      TIMESTAMP(6) NOT NULL
);

-- Supports both the reclaim-when-expired query and a future cleanup job.
CREATE INDEX idx_idempotency_key_expires_at ON idempotency_key (expires_at);
