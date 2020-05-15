CREATE TYPE public.continent AS ENUM (
  'ASIA',
  'AFRICA',
  'ANTARCTICA',
  'AUSTRALIA',
  'EUROPE',
  'NORTH_AMERICA',
  'SOUTH_AMERICA'
);

ALTER TABLE public.campaigns
ADD COLUMN continent continent;

CREATE INDEX IF NOT EXISTS campaigns_continent_idx
ON public.campaigns
USING btree (continent);