import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M38 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(38)(
    List(
      sqlu"""
INSERT INTO datasources (
  id, created_at, created_by, modified_at, modified_by, organization_id,
  name, visibility, color_correction, extras
) VALUES (
  '697a0b91-b7a8-446e-842c-97cda155554d', now(), 'default', now(),
  'default', 'dfac6307-b5ef-43f7-beda-b9f208bb7726', 'Landsat 8', 'PUBLIC',
  '{"redBand": 3, "greenBand": 2, "blueBand": 1, "redGamma": 0.5, "blueGamma": 0.5, "greenGamma": 0.5, "brightness": -6, "contrast": 9, "alpha": 0.4, "beta": 13, "min": 0, "max": 20000, "equalize": false}',
  '{}'
);
INSERT INTO datasources (
  id, created_at, created_by, modified_at, modified_by, organization_id,
  name, visibility, color_correction, extras
) VALUES (
  '4a50cb75-815d-4fe5-8bc1-144729ce5b42', now(), 'default', now(),
  'default', 'dfac6307-b5ef-43f7-beda-b9f208bb7726', 'Sentinel-2', 'PUBLIC',
  '{"redBand": 4, "greenBand": 3, "blueBand": 2, "redGamma": 0.5, "blueGamma": 0.5, "greenGamma": 0.5, "brightness": -6, "contrast": 9, "alpha": 0.4, "beta": 13, "min": 0, "max": 20000, "equalize": false}',
  '{}'
);

ALTER TABLE scenes ADD COLUMN datasource_id UUID;

UPDATE scenes SET datasource_id = datasources.id FROM datasources
  WHERE scenes.datasource = datasources.name;

ALTER TABLE scenes ADD FOREIGN KEY (datasource_id) REFERENCES
  datasources (id);

ALTER TABLE scenes DROP COLUMN datasource;
ALTER TABLE scenes RENAME COLUMN datasource_id TO datasource;
"""
    ))
}
