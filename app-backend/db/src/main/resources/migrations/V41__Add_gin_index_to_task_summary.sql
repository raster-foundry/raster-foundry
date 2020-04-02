-- add an index on task summary column in case we need sorting
CREATE INDEX CONCURRENTLY IF NOT EXISTS annotation_projects_task_status_summary 
ON public.annotation_projects
USING gin (task_status_summary);