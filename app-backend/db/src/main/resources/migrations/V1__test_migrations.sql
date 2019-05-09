-- This is the migration that the database will be baselined to.
-- Effectively, this means that the migration is never run
CREATE TABLE test_table(
  id UUID PRIMARY KEY NOT NULL,
  details TEXT
);
