CREATE TYPE public.annotation_project_type AS ENUM (
    'DETECTION',
    'CLASSIFICATION',
    'SEGMENTATION'
);

CREATE TYPE public.tile_layer_type AS ENUM (
    'TMS',
    'MVT'
);

CREATE TABLE public.annotation_projects (
    id uuid primary key,
    created_at timestamp without time zone NOT NULL,
    created_by character varying(255) NOT NULL references users(id),
    name text NOT NULL,
    project_type public.annotation_project_type NOT NULL,
    task_size_meters integer NOT NULL,
    aoi public.geometry(Geometry,3857),
    organization_id uuid NOT NULL references organizations(id),
    labelers_team_id uuid NOT NULL references teams(id),
    validators_team_id uuid NOT NULL references teams(id),
    project_id uuid references projects(id)
);

CREATE TABLE public.tiles(
  id uuid primary key,
  name text NOT NULL,
  url text NOT NULL,
  is_default boolean NOT NULL default false,
  is_overlay boolean NOT NULL default false,
  layer_type tile_layer_type NOT NULL default 'TMS',
  annotation_project_id uuid NOT NULL references annotation_projects(id)
);

CREATE TABLE public.annotation_label_class_groups (
    id uuid primary key,
    name text NOT NULL,
    annotation_project_id uuid NOT NULL references annotation_projects(id),
    idx int default 0 NOT NULL
);

CREATE TABLE public.annotation_label_classes (
    id uuid primary key,
    name text NOT NULL,
    annotation_label_group_id uuid NOT NULL references annotation_label_class_groups(id),
    color_hex_code text NOT NULL,
    is_default boolean default false,
    is_determinant boolean default false,
    idx int NOT NULL default 0
);

CREATE TABLE public.annotation_labels (
    id uuid primary key,
    created_at timestamp without time zone NOT NULL,
    created_by character varying(255) NOT NULL references users(id),
    annotation_project_id uuid NOT NULL references annotation_projects(id),
    annotation_task_id uuid NOT NULL references tasks(id),
    geometry public.geometry(Geometry,3857)
);

CREATE INDEX annotation_labels_annotation_project_id_idx
  ON annotation_labels (annotation_project_id);

CREATE TABLE annotation_labels_annotation_label_classes (
  annotation_label_id uuid NOT NULL references annotation_labels(id),
  annotation_class_id uuid NOT NULL references annotation_label_classes(id)
);

CREATE INDEX annotation_labels_annotation_label_classes_label_id_idx
  ON annotation_labels_annotation_label_classes (annotation_label_id);
