-- delete annotation label classes on annotation label class group deletion
ALTER TABLE annotation_label_classes
  DROP CONSTRAINT annotation_label_classes_annotation_label_group_id_fkey;
ALTER TABLE annotation_label_classes
  ADD CONSTRAINT annotation_label_classes_annotation_label_group_id_fkey
  FOREIGN KEY (annotation_label_group_id) REFERENCES annotation_label_class_groups(id)
  ON DELETE CASCADE;

-- delete annotation label class groups on annotation project deletion
ALTER TABLE annotation_label_class_groups
  DROP CONSTRAINT annotation_label_class_groups_annotation_project_id_fkey;
ALTER TABLE annotation_label_class_groups
  ADD CONSTRAINT annotation_label_class_groups_annotation_project_id_fkey
  FOREIGN KEY (annotation_project_id) REFERENCES annotation_projects(id)
  ON DELETE CASCADE;

-- delete tile layers on annotation project deletion
ALTER TABLE tiles
  DROP CONSTRAINT tiles_annotation_project_id_fkey;
ALTER TABLE tiles
  ADD CONSTRAINT tiles_annotation_project_id_fkey
  FOREIGN KEY (annotation_project_id) REFERENCES annotation_projects(id)
  ON DELETE CASCADE;

-- delete annotations on project deletion
ALTER TABLE annotation_labels
  DROP CONSTRAINT annotation_labels_annotation_project_id_fkey;
ALTER TABLE annotation_labels
  ADD CONSTRAINT annotation_labels_annotation_project_id_fkey
  FOREIGN KEY (annotation_project_id) REFERENCES annotation_projects(id)
  ON DELETE CASCADE;

-- delete annotation <-> class relations when annotations are deleted
ALTER TABLE annotation_labels_annotation_label_classes
  DROP CONSTRAINT annotation_labels_annotation_label_cla_annotation_label_id_fkey;
ALTER TABLE annotation_labels_annotation_label_classes
  ADD CONSTRAINT annotation_labels_annotation_label_cla_annotation_label_id_fkey
  FOREIGN KEY (annotation_label_id) REFERENCES annotation_labels(id)
  ON DELETE CASCADE;
