-- update campaigns table to include is_active column
ALTER TABLE public.campaigns
ADD COLUMN is_active boolean NOT NULL default true;

CREATE INDEX IF NOT EXISTS campaigns_is_active_idx
ON public.campaigns
USING btree (is_active);