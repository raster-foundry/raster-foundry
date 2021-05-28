ALTER TABLE public.annotation_labels
ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE,
ADD COLUMN session_id uuid REFERENCES task_sessions(id) ON DELETE CASCADE;