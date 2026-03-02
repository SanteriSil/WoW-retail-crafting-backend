CREATE TABLE recipe_optional_ingredient_groups (
    id BIGSERIAL PRIMARY KEY,
    recipe_id BIGINT NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
    slot_index SMALLINT NOT NULL,
    label VARCHAR(255),
    CONSTRAINT uq_recipe_optional_groups_recipe_slot UNIQUE (recipe_id, slot_index)
);

CREATE TABLE recipe_optional_ingredients (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES recipe_optional_ingredient_groups(id) ON DELETE CASCADE,
    item_id BIGINT NOT NULL REFERENCES items(id),
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    CONSTRAINT uq_recipe_optional_ingredients_group_item UNIQUE (group_id, item_id)
);
