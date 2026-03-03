CREATE TABLE character_recipes (
    id             BIGSERIAL   PRIMARY KEY,
    character_id   BIGINT      NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    recipe_id      BIGINT      NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    assigned_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (character_id, recipe_id)
);
CREATE INDEX idx_character_recipes_character ON character_recipes (character_id);
CREATE INDEX idx_character_recipes_recipe    ON character_recipes (recipe_id);
