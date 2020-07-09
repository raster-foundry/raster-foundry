ALTER TABLE label_class_history
  DROP CONSTRAINT "label_class_history_child_label_class_id_fkey",
  ADD CONSTRAINT "label_class_history_child_label_class_id_fkey"
    FOREIGN KEY (child_label_class_id) REFERENCES annotation_label_classes(id) ON DELETE CASCADE,
  DROP CONSTRAINT "label_class_history_parent_label_class_id_fkey",
  ADD CONSTRAINT "label_class_history_parent_label_class_id_fkey"
    FOREIGN KEY (parent_label_class_id) REFERENCES annotation_label_classes(id) ON DELETE CASCADE;
