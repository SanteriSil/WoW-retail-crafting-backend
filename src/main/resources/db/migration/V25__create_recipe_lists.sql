CREATE TABLE recipe_lists (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE recipe_list_entries (
    list_id   BIGINT NOT NULL REFERENCES recipe_lists (id) ON DELETE CASCADE,
    recipe_id BIGINT NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    PRIMARY KEY (list_id, recipe_id)
);

CREATE INDEX idx_recipe_list_entries_recipe ON recipe_list_entries (recipe_id);