-- Add a table for human-in-the-loop jobs

CREATE TYPE public.hitl_job_status AS ENUM (
    'NOTRUN',
    'TORUN',
    'RUNNING',
    'RAN',
    'FAILED'
);

ALTER TYPE public.hitl_job_status OWNER TO rasterfoundry;

CREATE TABLE public.hitl_jobs (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_by CHARACTER VARYING(255) NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    modified_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    owner CHARACTER VARYING(255) NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    campaign_id UUID NOT NULL REFERENCES public.campaigns(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES public.annotation_projects(id) ON DELETE CASCADE,
    status public.hitl_job_status NOT NULL,
    version int default 0 NOT NULL
);

CREATE INDEX hitl_jobs_owner_idx ON public.hitl_jobs USING btree(owner);

CREATE INDEX hitl_jobs_created_by_idx ON public.hitl_jobs USING btree(created_by);

CREATE INDEX hitl_jobs_status_idx ON public.hitl_jobs USING btree(status);

CREATE INDEX hitl_jobs_campaign_id_idx ON public.hitl_jobs USING btree(campaign_id);

CREATE INDEX hitl_jobs_project_id_idx ON public.hitl_jobs USING btree(project_id);

CREATE INDEX hitl_jobs_version_idx ON public.hitl_jobs USING btree(version);