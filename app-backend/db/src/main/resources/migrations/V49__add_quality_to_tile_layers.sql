CREATE TYPE public.tile_layer_quality AS ENUM (
  'NA',
  'GOOD',
  'BETTER',
  'BEST'
);

ALTER TABLE public.tiles ADD COLUMN quality tile_layer_quality;