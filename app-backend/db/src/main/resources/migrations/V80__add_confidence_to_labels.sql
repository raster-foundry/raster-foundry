ALTER TABLE
    annotation_labels
ADD
    COLUMN confidence real DEFAULT null;

CREATE INDEX IF NOT EXISTS annotation_labels_confidence_idx ON annotation_labels (confidence);