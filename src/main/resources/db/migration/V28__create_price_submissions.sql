CREATE TABLE IF NOT EXISTS price_submissions (
    id BIGSERIAL PRIMARY KEY,
    item_id BIGINT NOT NULL,
    submitted_price BIGINT NOT NULL,
    submitted_quantity BIGINT NOT NULL,
    source VARCHAR(64) NOT NULL,
    actor_discord_id BIGINT NOT NULL,
    actor_discord_username VARCHAR(255),
    audit_event_id BIGINT,
    batch_id VARCHAR(64) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_price_submissions_item
        FOREIGN KEY (item_id) REFERENCES items(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_price_submissions_audit_event
        FOREIGN KEY (audit_event_id) REFERENCES audit_events(id)
        ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_price_submissions_submitted_at
    ON price_submissions (submitted_at DESC);
CREATE INDEX IF NOT EXISTS idx_price_submissions_item_id
    ON price_submissions (item_id);
CREATE INDEX IF NOT EXISTS idx_price_submissions_actor_discord_id
    ON price_submissions (actor_discord_id);
CREATE INDEX IF NOT EXISTS idx_price_submissions_source
    ON price_submissions (source);
CREATE INDEX IF NOT EXISTS idx_price_submissions_batch_id
    ON price_submissions (batch_id);
