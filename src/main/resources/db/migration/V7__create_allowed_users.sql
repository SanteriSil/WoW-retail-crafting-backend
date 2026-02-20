CREATE TABLE allowed_users (
    discord_id BIGINT PRIMARY KEY,
    discord_username TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

INSERT INTO allowed_users (discord_id, discord_username) VALUES (148170052171071488, 'silkku');
