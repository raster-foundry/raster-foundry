-- Add a table for label stac export

CREATE TABLE public.stac_exports (
    id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    created_by character varying(255) NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    modified_at timestamp without time zone NOT NULL,
    modified_by character varying(255) REFERENCES public.users(id) ON DELETE SET NULL,
    owner character varying(255) NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    name character varying(255) NOT NULL,
    export_location text,
    export_status public.export_status NOT NULL,
    layer_definition jsonb NOT NULL,
    is_union boolean NOT NULL DEFAULT false,
    task_statuses text[] NOT NULL DEFAULT ARRAY['VALIDATED']::text[]
);
