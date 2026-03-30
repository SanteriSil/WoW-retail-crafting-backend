CREATE TABLE IF NOT EXISTS recipe_character_stat_overrides (
    id BIGSERIAL PRIMARY KEY,
    recipe_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    multicraft_percent REAL NOT NULL,
    resourcefulness_percent REAL NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_recipe_character_stat_override UNIQUE (recipe_id, character_id),
    CONSTRAINT fk_recipe_character_stat_override_recipe
        FOREIGN KEY (recipe_id) REFERENCES recipes(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_recipe_character_stat_override_character
        FOREIGN KEY (character_id) REFERENCES characters(id)
        ON DELETE CASCADE,
    CONSTRAINT ck_recipe_character_stat_override_multicraft_non_negative
        CHECK (multicraft_percent >= 0),
    CONSTRAINT ck_recipe_character_stat_override_resourcefulness_non_negative
        CHECK (resourcefulness_percent >= 0)
);

CREATE INDEX IF NOT EXISTS idx_recipe_character_stat_overrides_character_id
    ON recipe_character_stat_overrides (character_id);
CREATE INDEX IF NOT EXISTS idx_recipe_character_stat_overrides_recipe_id
    ON recipe_character_stat_overrides (recipe_id);
