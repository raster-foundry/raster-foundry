ALTER TABLE public.tasks
ADD COLUMN annotation_project_id uuid references annotation_projects(id);

ALTER TABLE public.tasks
ADD CONSTRAINT annotation_project_id_not_null CHECK (annotation_project_id IS NOT NULL) NOT VALID;

UPDATE public.tasks AS t
SET annotation_project_id = ap.id
FROM annotation_projects AS ap
WHERE ap.project_id = t.project_id;

ALTER TABLE public.tasks VALIDATE CONSTRAINT annotation_project_id_not_null;

ALTER TABLE public.tasks 
DROP COLUMN project_id,
DROP COLUMN project_layer_id;