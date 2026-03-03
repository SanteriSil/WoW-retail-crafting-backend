CREATE TABLE access_requests (
    id               BIGSERIAL    PRIMARY KEY,
    discord_id       BIGINT       NOT NULL,
    discord_username VARCHAR(255) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reviewed_by      BIGINT,
    reviewed_at      TIMESTAMPTZ,
    CONSTRAINT uq_access_request_discord_pending UNIQUE (discord_id)
);
