-- Cascade the deletion of task actions when tasks are deleted

ALTER TABLE public.task_actions
DROP CONSTRAINT "task_actions_task_id_fkey",
ADD CONSTRAINT "task_actions_task_id_fkey" FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE;
