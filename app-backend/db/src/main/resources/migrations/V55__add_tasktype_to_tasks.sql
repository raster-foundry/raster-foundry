CREATE TYPE public.task_type AS ENUM (
  'LABEL',
  'REVIEW'
);

ALTER TABLE public.tasks
ADD COLUMN task_type task_type NOT NULL DEFAULT 'LABEL',
ADD COLUMN parent_task_id UUID REFERENCES tasks(id),
ADD COLUMN reviews JSONB NOT NULL DEFAULT '{}';

CREATE INDEX CONCURRENTLY idx_task_parent ON public.tasks(parent_task_id);
