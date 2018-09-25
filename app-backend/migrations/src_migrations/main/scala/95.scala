import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M95 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(95)(
    List(
      sqlu"""
INSERT INTO datasources (
    id,
    created_at,
    created_by,
    modified_at,
    modified_by,
    organization_id,
    name,
    visibility,
    extras,
    composites,
    owner,
    bands
) VALUES (
    'e4d1b0a0-99ee-493d-8548-53df8e20d2aa',
    now(),
    'default',
    now(),
    'default',
    'dfac6307-b5ef-43f7-beda-b9f208bb7726',
    '4-band PlanetScope',
    'PUBLIC',
    '{}',
    '{
        "natural":{
            "label": "Natural Color",
            "value": {
                "redBand": 2,
                "greenBand": 1,
                "blueBand": 0
            }
        },
        "nir":{
            "label": "Color Infrared - NIR",
            "value": {
                "redBand": 3,
                "greenBand": 2,
                "blueBand": 1
            }
        }
    }',
    'default',
    '[
        {
            "name": "Blue",
            "number": "0",
            "wavelength": 475.0
        }, {
            "name": "Green",
            "number": "1",
            "wavelength": 555.0
        }, {
            "name": "Red",
            "number": "2",
            "wavelength": 657.5
        }, {
            "name": "Near Infrared",
            "number": "3",
            "wavelength": 805.0
        }
    ]'
);

UPDATE scenes SET datasource = 'e4d1b0a0-99ee-493d-8548-53df8e20d2aa' where
datasource in ('7a150247-8b12-4174-bbae-7b11c2a268cd', '61c8972f-9461-4e7d-ae2a-46cfb1f94810', '3d5b6e55-c9b7-4c86-b913-f45ae39296f7');

UPDATE uploads SET datasource = 'e4d1b0a0-99ee-493d-8548-53df8e20d2aa' where
datasource in ('7a150247-8b12-4174-bbae-7b11c2a268cd', '61c8972f-9461-4e7d-ae2a-46cfb1f94810', '3d5b6e55-c9b7-4c86-b913-f45ae39296f7');

DELETE from datasources where datasources.id in ('7a150247-8b12-4174-bbae-7b11c2a268cd', '61c8972f-9461-4e7d-ae2a-46cfb1f94810', '3d5b6e55-c9b7-4c86-b913-f45ae39296f7');
"""
    ))
}
