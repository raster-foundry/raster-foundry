ALTER TABLE tasks DROP CONSTRAINT tasks_annotation_project_id_fkey;
ALTER TABLE tasks ADD CONSTRAINT tasks_annotation_project_id_fkey
  FOREIGN KEY (annotation_project_id)
  REFERENCES annotation_projects(id)
  ON DELETE CASCADE;

ALTER TABLE annotation_labels DROP CONSTRAINT annotation_labels_annotation_task_id_fkey;
ALTER TABLE annotation_labels ADD CONSTRAINT annotation_labels_annotation_task_id_fkey
  FOREIGN KEY (annotation_task_id)
  REFERENCES tasks(id)
  ON DELETE CASCADE;

ALTER TABLE annotation_projects DROP CONSTRAINT annotation_projects_created_by_fkey;
ALTER TABLE annotation_projects ADD CONSTRAINT annotation_projects_owner_fkey
  FOREIGN KEY (owner)
  REFERENCES users(id)
  ON DELETE CASCADE;

ALTER TABLE annotation_projects DROP CONSTRAINT annotation_projects_labelers_team_id_fkey;
ALTER TABLE annotation_projects ADD CONSTRAINT annotation_projects_labelers_team_id_fkey
  FOREIGN KEY (labelers_team_id)
  REFERENCES teams(id)
  ON DELETE SET NULL;

ALTER TABLE annotation_projects DROP CONSTRAINT annotation_projects_validators_team_id_fkey;
ALTER TABLE annotation_projects ADD CONSTRAINT annotation_projects_validators_team_id_fkey
  FOREIGN KEY (validators_team_id)
  REFERENCES teams(id)
  ON DELETE SET NULL;

ALTER TABLE annotation_projects DROP CONSTRAINT annotation_projects_project_id_fkey;
ALTER TABLE annotation_projects ADD CONSTRAINT annotation_projects_project_id_fkey
  FOREIGN KEY (project_id)
  REFERENCES projects(id)
  ON DELETE SET NULL;

ALTER TABLE annotation_labels DROP CONSTRAINT annotation_labels_created_by_fkey;
ALTER TABLE annotation_labels ADD CONSTRAINT annotation_labels_created_by_fkey
  FOREIGN KEY (created_by)
  REFERENCES users(id)
  ON DELETE CASCADE;
