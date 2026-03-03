CREATE TABLE characters (
    id          BIGSERIAL    PRIMARY KEY,
    discord_id  BIGINT       NOT NULL,
    name        VARCHAR(50)  NOT NULL,
    realm       VARCHAR(100) NOT NULL,
    icon_url    VARCHAR(1024),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (discord_id, name, realm)
);
CREATE INDEX idx_characters_discord_id ON characters (discord_id);
