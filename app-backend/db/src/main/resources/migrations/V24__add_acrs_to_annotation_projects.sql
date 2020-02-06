ALTER TABLE public.annotation_projects
  ADD COLUMN acrs text[] NOT NULL DEFAULT '{}' :: text[];
ALTER TABLE public.annotation_projects
  RENAME COLUMN created_by TO owner;

UPDATE public.annotation_projects
  SET acrs = projects.acrs
  FROM projects
  WHERE projects.id = annotation_projects.project_id;
