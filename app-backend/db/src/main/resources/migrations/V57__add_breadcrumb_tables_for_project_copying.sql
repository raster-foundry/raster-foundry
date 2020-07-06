CREATE TABLE public.label_class_history (
    parent_label_class_id uuid not null references annotation_label_classes (id),
    child_label_class_id uuid not null references annotation_label_classes (id)
);

CREATE INDEX IF NOT EXISTS label_class_history_parent_label_class_id_idx
ON public.label_class_history (parent_label_class_id);

CREATE INDEX IF NOT EXISTS label_class_history_child_label_class_id_idx
ON public.label_class_history (child_label_class_id);