-- Seed initial professions: ensure General is the first value

-- Insert with explicit ids to guarantee 'General' is id=1 and 'Enchanting' is id=2
-- Use idempotent upserts so this script can be re-run safely

INSERT INTO professions (id, name)
VALUES (1, 'General')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO professions (id, name)
VALUES (2, 'Enchanting')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

-- Ensure the identity sequence (if any) is at least the max(id)
DO $$
BEGIN
  PERFORM setval(pg_get_serial_sequence('professions','id'), (SELECT GREATEST(COALESCE(MAX(id),0), 2) FROM professions), true);
EXCEPTION WHEN undefined_function THEN
  -- If pg_get_serial_sequence doesn't exist (non-Postgres DB), ignore
  NULL;
END
$$;
