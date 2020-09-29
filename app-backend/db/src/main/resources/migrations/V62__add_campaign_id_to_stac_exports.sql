ALTER TABLE stac_exports ADD COLUMN campaign_id uuid REFERENCES campaigns(id);
