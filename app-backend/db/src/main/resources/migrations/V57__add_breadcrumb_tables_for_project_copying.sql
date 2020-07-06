CREATE TABLE public.label_class_history (
    parent_label_class_id uuid not null,
    child_label_class_id uuid not null
);

CREATE INDEX IF NOT EXISTS label_class_history_parent_label_class_id_idx
ON public.label_class_history (parent_label_class_id);

CREATE INDEX IF NOT EXISTS label_class_history_child_label_class_id_idx
ON public.label_class_history (child_label_class_id);