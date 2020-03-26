-- add a jsonb column to annotation_projects table for task status summary
ALTER TABLE public.annotation_projects 
ADD COLUMN task_status_summary jsonb DEFAULT '{"UNLABELED": 0, "LABELING_IN_PROGRESS": 0, "LABELED": 0, "VALIDATION_IN_PROGRESS": 0, "VALIDATED": 0 }'::jsonb NOT NULL;

-- populate task_status_summary column for all annotation projects
UPDATE public.annotation_projects
SET task_status_summary = statuses.summary
FROM (
  SELECT
    annotation_projects.id as annotation_project_id, 
    CREATE_TASK_SUMMARY(
      jsonb_object_agg(
        statuses.status,
        statuses.status_count
      )
    ) AS summary
  FROM (
    SELECT status, annotation_project_id, COUNT(id) AS status_count
    FROM tasks
    GROUP BY status, annotation_project_id
  ) statuses
  JOIN annotation_projects
  ON statuses.annotation_project_id = annotation_projects.id
  GROUP BY annotation_projects.id
) statuses
WHERE statuses.annotation_project_id = id;

-- define the trigger function to update task summary for annotation projects
CREATE OR REPLACE FUNCTION UPDATE_PROJECT_TASK_SUMMARY()
  RETURNS trigger AS
$BODY$
DECLARE
  project_id uuid;
BEGIN
  -- the NEW variable holds row for INSERT/UPDATE operations
  -- the OLD variable holds row for DELETE operations
  -- store the annotation project ID
  IF TG_OP = 'INSERT' OR TG_OP = 'UPDATE' THEN
    project_id := NEW.annotation_project_id;
  ELSE
    project_id := OLD.annotation_project_id;
  END IF;
  -- update task summary for the stored annotation project
  UPDATE annotation_projects
  SET task_status_summary = CREATE_TASK_SUMMARY(jsonb_object_agg(
    statuses.status,
    statuses.status_count
  ))
  FROM (
    SELECT status, COUNT(id) AS status_count
    FROM tasks
    WHERE annotation_project_id = project_id
    GROUP BY status
  ) statuses
  WHERE annotation_project_id = project_id;

  -- result is ignored since this is an AFTER trigger
  RETURN NULL;
END;
$BODY$
LANGUAGE 'plpgsql';

-- add a trigger to INSERT OR UPDATE OR DELETE operations on tasks table
CREATE TRIGGER update_annotation_project_task_summary
  AFTER INSERT OR UPDATE OR DELETE
  ON tasks
  FOR EACH ROW
  EXECUTE PROCEDURE UPDATE_PROJECT_TASK_SUMMARY();