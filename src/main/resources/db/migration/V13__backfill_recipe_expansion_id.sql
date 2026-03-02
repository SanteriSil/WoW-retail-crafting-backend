UPDATE recipes
SET expansion_id = (
    SELECT id
    FROM expansions
    WHERE slug = 'midnight'
)
WHERE expansion_id IS NULL;

ALTER TABLE recipes
    ALTER COLUMN expansion_id SET NOT NULL;
