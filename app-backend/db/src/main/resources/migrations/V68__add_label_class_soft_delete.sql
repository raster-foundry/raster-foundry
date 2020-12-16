ALTER TABLE public.annotation_label_classes
    ADD COLUMN "is_active" BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE public.annotation_label_class_groups
    ADD COLUMN "is_active" BOOLEAN NOT NULL DEFAULT TRUE;