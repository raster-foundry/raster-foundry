CREATE TYPE public.label_geom_type AS ENUM (
  'POINT',
  'POLYGON'
);

ALTER TABLE public.annotation_label_classes
ADD COLUMN geometry_type label_geom_type,
ADD COLUMN description text;