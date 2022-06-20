ALTER TABLE
    annotation_labels
ADD
    COLUMN hitl_version_id UUID DEFAULT NULL REFERENCES public.hitl_jobs(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS annotation_labels_hitl_version_id_idx ON annotation_labels (hitl_version_id);