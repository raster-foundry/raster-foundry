ALTER TABLE public.campaigns
ADD COLUMN tags text[] NOT NULL DEFAULT '{}' :: text[];

CREATE INDEX IF NOT EXISTS campaigns_tags_idx ON public.campaigns USING gin (tags);