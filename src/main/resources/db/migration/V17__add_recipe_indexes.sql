CREATE INDEX idx_recipes_profession_expansion
    ON recipes(profession_id, expansion_id)
    WHERE deleted = FALSE;

CREATE INDEX idx_recipes_output_item
    ON recipes(output_item_id)
    WHERE deleted = FALSE;

CREATE INDEX idx_recipes_expansion
    ON recipes(expansion_id)
    WHERE deleted = FALSE;

CREATE INDEX idx_recipe_ingredients_item
    ON recipe_ingredients(item_id);
