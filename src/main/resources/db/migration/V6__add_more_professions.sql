-- Insert missing crafting professions
-- Use explicit ids and idempotent upserts so this migration is safe to re-run.

INSERT INTO professions (id, name) VALUES (2, 'Enchanting')
  ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO professions (id, name) VALUES (3, 'Alchemy')
  ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO professions (id, name) VALUES (4, 'Blacksmithing')
  ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO professions (id, name) VALUES (5, 'Cooking')
  ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO professions (id, name) VALUES (6, 'Engineering')
  ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO professions (id, name) VALUES (7, 'Inscription')
  ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO professions (id, name) VALUES (8, 'Jewelcrafting')
  ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO professions (id, name) VALUES (9, 'Leatherworking')
  ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO professions (id, name) VALUES (10, 'Tailoring')
  ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

-- Ensure sequence is at least the max id
DO $$
BEGIN
  PERFORM setval(pg_get_serial_sequence('professions','id'), (SELECT GREATEST(COALESCE(MAX(id),0), 10) FROM professions), true);
EXCEPTION WHEN undefined_function THEN
  NULL;
END
$$;
