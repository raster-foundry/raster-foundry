--- Adds fields to scenes for storing raster source metadata
ALTER TABLE scenes
ADD COLUMN data_path text,
ADD COLUMN crs text,
ADD COLUMN band_count integer,
ADD COLUMN cell_type text,
ADD COLUMN grid_extent jsonb,
ADD COLUMN resolutions jsonb,
ADD COLUMN no_data_value double precision;
