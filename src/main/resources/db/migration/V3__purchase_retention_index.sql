-- Supports the anonymous-purchase retention cleanup job (issue #17), which
-- deletes rows by created_at; without this index that query is a full table
-- scan. Mirrors the precedent V2 set for idempotency_key.expires_at.

CREATE INDEX idx_purchase_created_at ON purchase (created_at);
