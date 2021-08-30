ALTER TABLE stac_exports
    ADD COLUMN export_asset_types varchar[] DEFAULT NULL;
