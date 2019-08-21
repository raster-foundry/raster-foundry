-- Add a table for label stac export

CREATE TABLE public.stac_exports (
    id uuid primary key,
    created_at timestamp without time zone NOT NULL,
    created_by character varying(255) NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    modified_at timestamp without time zone NOT NULL,
    owner character varying(255) NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    name character varying(255) NOT NULL,
    export_location text,
    export_status public.export_status NOT NULL,
    layer_definitions jsonb NOT NULL,
    task_statuses text[] NOT NULL DEFAULT ARRAY['VALIDATED']::text[]
);

CREATE INDEX stac_export_owner_idx ON public.stac_exports USING btree(owner);

CREATE INDEX stac_export_created_by_idx ON public.stac_exports USING btree(created_by);

CREATE INDEX stac_export_status_idx ON public.stac_exports USING btree(export_status);