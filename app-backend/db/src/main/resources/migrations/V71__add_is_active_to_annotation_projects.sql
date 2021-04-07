-- update annotation_projects table to include is_active column
ALTER TABLE public.annotation_projects
ADD COLUMN is_active boolean NOT NULL default true;