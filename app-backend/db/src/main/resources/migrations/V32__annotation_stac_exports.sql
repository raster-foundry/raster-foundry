ALTER TABLE stac_exports DROP COLUMN layer_definitions;

ALTER TABLE stac_exports ADD COLUMN annotation_project_id UUID;
ALTER TABLE stac_exports ADD CONSTRAINT stac_exports_annotation_project_id_fkey
  FOREIGN KEY (annotation_project_id)
  REFERENCES annotation_projects(id)
  ON DELETE SET NULL;
