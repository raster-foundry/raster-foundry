ALTER TABLE stac_exports ADD COLUMN campaign_id uuid REFERENCES campaigns(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS stac_exports_campaign_id_idx ON stac_exports (campaign_id);
