ALTER TABLE recipes
    ALTER COLUMN multicraft_multiplier SET DEFAULT 1.25;

UPDATE recipes
SET multicraft_multiplier = 1.25
WHERE multicraft_multiplier = 1.2;
