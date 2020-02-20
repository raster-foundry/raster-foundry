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
