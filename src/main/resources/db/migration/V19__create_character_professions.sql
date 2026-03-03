CREATE TABLE character_professions (
    id                        BIGSERIAL PRIMARY KEY,
    character_id              BIGINT    NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    profession_id             INT       NOT NULL REFERENCES professions (id),
    multicraft_percent        REAL      NOT NULL DEFAULT 0
                              CHECK (multicraft_percent >= 0 AND multicraft_percent <= 100),
    resourcefulness_percent   REAL      NOT NULL DEFAULT 0
                              CHECK (resourcefulness_percent >= 0 AND resourcefulness_percent <= 100),
    UNIQUE (character_id, profession_id)
);
