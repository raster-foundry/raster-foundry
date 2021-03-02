ALTER TABLE annotation_label_class_groups
  DROP CONSTRAINT "annotation_label_class_groups_campaign_id_fkey",
  ADD CONSTRAINT "annotation_label_class_groups_campaign_id_fkey"
    FOREIGN KEY (campaign_id) REFERENCES campaigns(id) ON DELETE CASCADE;