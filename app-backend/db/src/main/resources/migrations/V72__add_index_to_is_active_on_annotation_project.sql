-- add btree index on is_active field of annotation_projects table
CREATE INDEX IF NOT EXISTS annotation_projects_is_active_idx
ON public.annotation_projects
USING btree (is_active);