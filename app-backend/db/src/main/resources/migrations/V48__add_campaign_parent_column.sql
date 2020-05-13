ALTER TABLE public.campaigns
ADD COLUMN parent_campaign_id uuid REFERENCES campaigns(id);

CREATE INDEX IF NOT EXISTS campaigns_parent_campaign_idx
ON public.campaigns
USING btree (parent_campaign_id);
