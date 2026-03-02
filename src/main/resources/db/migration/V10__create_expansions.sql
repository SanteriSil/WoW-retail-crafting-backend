CREATE TABLE expansions (
    id   SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    slug VARCHAR(100) NOT NULL UNIQUE
);

INSERT INTO expansions (name, slug) VALUES ('Midnight', 'midnight');
