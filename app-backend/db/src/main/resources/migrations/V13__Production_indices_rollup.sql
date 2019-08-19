CREATE INDEX IF NOT EXISTS annotation_group_idx ON public.annotations USING btree (annotation_group);
CREATE INDEX IF NOT EXISTS project_layer_id_idx ON public.annotations USING btree (project_layer_id);
CREATE INDEX IF NOT EXISTS tasks_actions_task_id_idx ON public.task_actions USING btree (task_id);
CREATE INDEX IF NOT EXISTS tasks_project_id_idx ON public.tasks USING btree (project_id);
CREATE INDEX IF NOT EXISTS tasks_project_layer_id_idx ON public.tasks USING btree (project_layer_id);
CREATE INDEX IF NOT EXISTS tasks_status_idx ON public.tasks USING btree (status); -- this is probably not effective
