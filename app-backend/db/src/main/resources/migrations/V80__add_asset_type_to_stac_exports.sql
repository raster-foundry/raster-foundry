CREATE TYPE export_asset_type AS ENUM ('TILE_LAYER', 'SIGNED_URL', 'IMAGES');

ALTER TABLE stac_exports
    ADD COLUMN export_asset_types export_asset_type[] DEFAULT NULL;
