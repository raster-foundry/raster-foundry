ALTER TABLE
    tasks
ADD
    COLUMN priority_score REAL DEFAULT NULL;

CREATE INDEX IF NOT EXISTS tasks_score_idx ON tasks (priority_score);