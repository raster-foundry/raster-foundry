ALTER TABLE stac_exports ADD COLUMN  license jsonb NOT NULL default '{"license": "Apache-2.0"}';
