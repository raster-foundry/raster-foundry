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