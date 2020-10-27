ALTER TABLE public.annotation_label_class_groups
ALTER COLUMN annotation_project_id DROP NOT NULL,
ADD COLUMN campaign_id UUID REFERENCES campaigns(id);