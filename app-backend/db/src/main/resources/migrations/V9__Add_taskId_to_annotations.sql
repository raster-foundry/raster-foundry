ALTER TABLE annotations
ADD COLUMN task_id uuid REFERENCES tasks(id) ON DELETE SET NULL;
