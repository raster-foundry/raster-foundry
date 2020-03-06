ALTER TABLE uploads DROP CONSTRAINT uploads_annotation_project_id_fkey;
ALTER TABLE uploads ADD CONSTRAINT uploads_annotation_project_id_fkey
  FOREIGN KEY (annotation_project_id)
  REFERENCES annotation_projects(id)
  ON DELETE CASCADE;
