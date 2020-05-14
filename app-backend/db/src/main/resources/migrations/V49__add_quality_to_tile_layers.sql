CREATE TYPE public.tile_layer_quality AS ENUM (
  'GOOD',
  'BETTER',
  'BEST'
);

ALTER TABLE public.tiles ADD COLUMN quality tile_layer_quality;