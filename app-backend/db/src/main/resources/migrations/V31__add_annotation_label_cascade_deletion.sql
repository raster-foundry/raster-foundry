ALTER TABLE annotation_labels 
DROP CONSTRAINT annotation_labels_annotation_task_id_fkey,
DROP CONSTRAINT annotation_labels_created_by_fkey;

ALTER TABLE annotation_labels 
ADD CONSTRAINT annotation_labels_annotation_task_id_fkey
FOREIGN KEY (annotation_task_id) 
REFERENCES tasks (id) ON DELETE CASCADE;

ALTER TABLE annotation_labels 
ADD CONSTRAINT annotation_labels_created_by_fkey
FOREIGN KEY (created_by) 
REFERENCES users (id) ON DELETE CASCADE;