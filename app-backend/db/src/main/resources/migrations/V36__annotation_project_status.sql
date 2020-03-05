CREATE TYPE public.annotation_project_status AS ENUM (
    'WAITING',
    'QUEUED',
    'PROCESSING',
    'READY',
    'UNKNOWN_FAILURE',
    'TASK_GRID_FAILURE',
    'IMAGE_INGESTION_FAILURE'
);

ALTER TABLE public.annotation_projects 
ADD COLUMN status public.annotation_project_status;

-- set default values and add null constraint
ALTER TABLE public.annotation_projects ADD CONSTRAINT annotation_project_status_not_null CHECK (status IS NOT NULL) NOT VALID;

UPDATE public.annotation_projects SET status = 'READY' WHERE ready = true;
UPDATE public.annotation_projects SET status = 'UNKNOWN_FAILURE' WHERE ready = false;

ALTER TABLE public.annotation_projects VALIDATE CONSTRAINT annotation_project_status_not_null;

-- drop old column
ALTER TABLE annotation_projects DROP COLUMN ready;