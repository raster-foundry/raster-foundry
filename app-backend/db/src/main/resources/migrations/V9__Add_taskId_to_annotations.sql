BEGIN;

ALTER TABLE annotations
ADD COLUMN task_id uuid;

ALTER TABLE ONLY annotations
ADD CONSTRAINT annotations_task_id_fkey FOREIGN KEY (task_id) REFERENCES public.tasks(id) ON DELETE SET NULL;

COMMIT;
