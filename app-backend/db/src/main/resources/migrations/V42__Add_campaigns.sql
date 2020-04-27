CREATE TABLE public.campaigns (
    id uuid primary key,
    created_at timestamp without time zone NOT NULL,
    owner character varying(255) NOT NULL references users(id) ON DELETE CASCADE,
    name text NOT NULL,
    campaign_type public.annotation_project_type NOT NULL,
    acrs text[] NOT NULL DEFAULT '{}' :: text[]
);

ALTER TABLE public.annotation_projects
ADD COLUMN campaign_id uuid REFERENCES campaigns(id) ON DELETE CASCADE,
ADD COLUMN captured_at timestamp without time zone;

CREATE INDEX IF NOT EXISTS annotation_project_campaign_idx ON public.annotation_projects USING btree (campaign_id);
CREATE INDEX IF NOT EXISTS annotation_project_captured_at_idx ON public.annotation_projects USING btree (captured_at);
CREATE INDEX IF NOT EXISTS campaigns_name_idx ON public.campaigns USING btree (name);
CREATE INDEX IF NOT EXISTS campaigns_owner_idx ON public.campaigns USING btree (owner);
CREATE INDEX IF NOT EXISTS campaigns_acrs_idx ON public.campaigns USING gin (acrs);
CREATE INDEX IF NOT EXISTS campaigns_type_idx ON public.campaigns USING btree (campaign_type);