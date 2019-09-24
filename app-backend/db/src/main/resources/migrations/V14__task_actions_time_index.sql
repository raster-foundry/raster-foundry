CREATE INDEX IF NOT EXISTS task_actions_timestamp_idx ON public.task_actions USING btree (timestamp);
