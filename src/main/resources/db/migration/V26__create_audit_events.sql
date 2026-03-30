CREATE TABLE IF NOT EXISTS audit_events (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor_discord_id BIGINT,
    action_key VARCHAR(64) NOT NULL,
    entity_key VARCHAR(64) NOT NULL,
    entity_id VARCHAR(128),
    result_key VARCHAR(32) NOT NULL,
    metadata TEXT
);

CREATE INDEX IF NOT EXISTS idx_audit_events_occurred_at ON audit_events (occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_events_actor_discord_id ON audit_events (actor_discord_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_entity_key_entity_id ON audit_events (entity_key, entity_id);
