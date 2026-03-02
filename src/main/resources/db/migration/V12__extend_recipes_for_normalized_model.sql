ALTER TABLE recipes
    ADD COLUMN wowhead_spell_id BIGINT,
    ADD COLUMN expansion_id INTEGER,
    ADD COLUMN source VARCHAR(50) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN created_by BIGINT;

ALTER TABLE recipes
    ADD CONSTRAINT fk_recipes_expansion
    FOREIGN KEY (expansion_id) REFERENCES expansions(id);

CREATE UNIQUE INDEX ux_recipes_wowhead_spell_id_not_null
    ON recipes(wowhead_spell_id)
    WHERE wowhead_spell_id IS NOT NULL;
