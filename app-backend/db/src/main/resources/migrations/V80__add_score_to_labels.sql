ALTER TABLE
    annotation_labels
ADD
    COLUMN score real DEFAULT null;

CREATE INDEX IF NOT EXISTS annotation_labels_score_idx ON annotation_labels (score);