import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M124 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(124)(
    List(
      sqlu"""
INSERT INTO datasources (
    id,
    created_at,
    created_by,
    modified_at,
    modified_by,
    name,
    visibility,
    extras,
    composites,
    owner,
    bands
)
VALUES (
  '866b22ee-e3e5-43e3-8ba0-d538249ab3b6',
  now(),
  'default',
  now(),
  'default',
  'Generic',
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
      }
  }',
  'default',
  '[]'
),(
  'b5d02569-11f7-473d-be82-d1d4e1ce45f3',
  now(),
  'default',
  now(),
  'default',
  'Landsat 4 Multispectral Scanner (MSS)',
  'PUBLIC',
  '{}',
  '{
      "nir": {
          "label": "Color Infrared",
          "value": {
              "redBand": 2,
              "greenBand": 1,
              "blueBand": 0
          }
      },
      "nir2": {
          "label": "Color Infrared 2",
          "value": {
              "redBand": 3,
              "greenBand": 1,
              "blueBand": 0
          }
      }
  }',
  'default',
  '[
     {
       "name": "Green",
       "number": "1",
       "wavelength": 550
     },
     {
       "name": "Red",
       "number": "2",
       "wavelength": 650
     },
     {
       "name": "Near Infrared (NIR)",
       "number": "3",
       "wavelength": 750
     },
     {
       "name": "Near Infrared (NIR)",
       "number": "4",
       "wavelength": 950
     }
   ]'
), (
  'e8c4d923-5a73-430d-8fe4-53bd6a12ce6a',
  now(),
  'default',
  now(),
  'default',
  'Landsat 4 Thematic Mapper (TM)',
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
      "urban": {
          "label": "False Color (Urban)",
          "value": {
              "redBand": 6,
              "greenBand": 4,
              "blueBand": 2
          }
      },
      "veg":{
          "label": "Color Infrared (vegetation)",
          "value": {
              "redBand": 3,
              "greenBand": 2,
              "blueBand": 1
          }
      },
      "agr":{
          "label": "Agriculture",
          "value": {
              "redBand": 4,
              "greenBand": 3,
              "blueBand": 0
          }
      },
      "atmos":{
          "label": "Atmospheric Penetration",
          "value": {
              "redBand": 6,
              "greenBand": 4,
              "blueBand": 3
          }
      },
      "hveg":{
          "label": "Healthy Vegetation",
          "value": {
              "redBand": 3,
              "greenBand": 4,
              "blueBand": 0
          }
      },
      "landwater":{
          "label": "Land/Water",
          "value": {
              "redBand": 3,
              "greenBand": 4,
              "blueBand": 2
          }
      },
      "noatmos":{
          "label": "Natural With Atmospheric Removal",
          "value": {
              "redBand": 6,
              "greenBand": 4,
              "blueBand": 2
          }
      },
      "swir":{
          "label": "Shortwave Infrared",
          "value": {
              "redBand": 6,
              "greenBand": 3,
              "blueBand": 2
          }
      },
      "vega":{
          "label": "Vegetation Analysis",
          "value": {
              "redBand": 4,
              "greenBand": 3,
              "blueBand": 2
          }
      }
  }',
  'default',
  '[
     {
       "name": "Blue",
       "number": "1",
       "wavelength": 485
     },
     {
       "name": "Green",
       "number": "2",
       "wavelength": 560
     },
     {
       "name": "Red",
       "number": "3",
       "wavelength": 660
     },
     {
       "name": "Near Infrared (NIR)",
       "number": "4",
       "wavelength": 830
     },
     {
       "name": "Shortwave Infrared (SWIR) 1",
       "number": "5",
       "wavelength": 1650
     },
     {
       "name": "Thermal",
       "number": "6",
       "wavelength": 11450
     },
     {
       "name": "Shortwave Infrared (SWIR) 2",
       "number": "7",
       "wavelength": 2215

     }
   ]'
), (
  '7b205ec9-6cce-444d-998a-f34d379258b2',
  now(),
  'default',
  now(),
  'default',
  'Landsat 5 Multispectral Scanner (MSS)',
  'PUBLIC',
  '{}',
  '{
      "nir": {
          "label": "Color Infrared",
          "value": {
              "redBand": 2,
              "greenBand": 1,
              "blueBand": 0
          }
      },
      "nir": {
          "label": "Color Infrared 2",
          "value": {
              "redBand": 3,
              "greenBand": 1,
              "blueBand": 0
          }
      }
  }',
  'default',
  '[
     {
       "name": "Green",
       "number": "1",
       "wavelength": 550
     },
     {
       "name": "Red",
       "number": "2",
       "wavelength": 650
     },
     {
       "name": "Near Infrared (NIR)",
       "number": "3",
       "wavelength": 750
     },
     {
       "name": "Near Infrared (NIR)",
       "number": "4",
       "wavelength": 950
     }
   ]'
), (
  'a7b4a88e-4f69-477f-9784-343fb775ab12',
  now(),
  'default',
  now(),
  'default',
  'Landsat 5 Thematic Mapper (TM)',
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
      "urban": {
          "label": "False Color (Urban)",
          "value": {
              "redBand": 6,
              "greenBand": 4,
              "blueBand": 2
          }
      },
      "veg":{
          "label": "Color Infrared (vegetation)",
          "value": {
              "redBand": 3,
              "greenBand": 2,
              "blueBand": 1
          }
      },
      "agr":{
          "label": "Agriculture",
          "value": {
              "redBand": 4,
              "greenBand": 3,
              "blueBand": 0
          }
      },
      "atmos":{
          "label": "Atmospheric Penetration",
          "value": {
              "redBand": 6,
              "greenBand": 4,
              "blueBand": 3
          }
      },
      "hveg":{
          "label": "Healthy Vegetation",
          "value": {
              "redBand": 3,
              "greenBand": 4,
              "blueBand": 0
          }
      },
      "landwater":{
          "label": "Land/Water",
          "value": {
              "redBand": 3,
              "greenBand": 4,
              "blueBand": 2
          }
      },
      "noatmos":{
          "label": "Natural With Atmospheric Removal",
          "value": {
              "redBand": 6,
              "greenBand": 4,
              "blueBand": 2
          }
      },
      "swir":{
          "label": "Shortwave Infrared",
          "value": {
              "redBand": 6,
              "greenBand": 3,
              "blueBand": 2
          }
      },
      "vega":{
          "label": "Vegetation Analysis",
          "value": {
              "redBand": 4,
              "greenBand": 3,
              "blueBand": 2
          }
      }
  }',
  'default',
  '[
     {
       "name": "Blue",
       "number": "1",
       "wavelength": 485
     },
     {
       "name": "Green",
       "number": "2",
       "wavelength": 560
     },
     {
       "name": "Red",
       "number": "3",
       "wavelength": 660
     },
     {
       "name": "Near Infrared (NIR)",
       "number": "4",
       "wavelength": 830
     },
     {
       "name": "Shortwave Infrared (SWIR) 1",
       "number": "5",
       "wavelength": 1650
     },
     {
       "name": "Thermal",
       "number": "6",
       "wavelength": 11450
     },
     {
       "name": "Shortwave Infrared (SWIR) 2",
       "number": "7",
       "wavelength": 2215
     }
   ]'
), (
  '5a462d31-5744-4ab9-9e80-5dbcb118f72f',
  now(),
  'default',
  now(),
  'default',
  'Landsat 7 Enhanced Thematic Mapper Plus (ETM+)',
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
      "urban":{
          "label": "False Color (Urban)",
          "value": {
              "redBand": 7,
              "greenBand": 4,
              "blueBand": 2
          }
      },
      "veg":{
          "label": "Color Infrared (vegetation)",
          "value": {
              "redBand": 3,
              "greenBand": 2,
              "blueBand": 1
          }
      },
      "agr":{
          "label": "Agriculture",
          "value": {
              "redBand": 4,
              "greenBand": 3,
              "blueBand": 0
          }
      },
      "atmos":{
          "label": "Atmospheric Penetration",
          "value": {
              "redBand": 7,
              "greenBand": 4,
              "blueBand": 3
          }
      },
      "hveg":{
          "label": "Healthy Vegetation",
          "value": {
              "redBand": 3,
              "greenBand": 4,
              "blueBand": 0
          }
      },
      "landwater":{
          "label": "Land/Water",
          "value": {
              "redBand": 3,
              "greenBand": 4,
              "blueBand": 2
          }
      },
      "noatmos":{
          "label": "Natural With Atmospheric Removal",
          "value": {
              "redBand": 7,
              "greenBand": 4,
              "blueBand": 2
          }
      },
      "swir":{
          "label": "Shortwave Infrared",
          "value": {
              "redBand": 7,
              "greenBand": 3,
              "blueBand": 2
          }
      },
      "vega":{
          "label": "Vegetation Analysis",
          "value": {
              "redBand": 4,
              "greenBand": 3,
              "blueBand": 2
          }
      }
  }',
  'default',
  '[
     {
       "name": "Blue",
       "number": "1",
       "wavelength": 485
     },
     {
       "name": "Green",
       "number": "2",
       "wavelength": 560
     },
     {
       "name": "Red",
       "number": "3",
       "wavelength": 660
     },
     {
       "name": "Near Infrared (NIR)",
       "number": "4",
       "wavelength": 835
     },
     {
       "name": "Shortwave Infrared (SWIR) 1",
       "number": "5",
       "wavelength": 1650
     },
     {
       "name": "Thermal (60m)",
       "number": "6",
       "wavelength": 11450
     },
     {
       "name": "Thermal (30m resampled)",
       "number": "6a",
       "wavelength": 11450
     },
     {
       "name": "Shortwave Infrared (SWIR) 2",
       "number": "7",
       "wavelength": 2220
     },
     {
       "name": "Panchromatic",
       "number": "8",
       "wavelength": 710
     }
   ]'
);

INSERT INTO access_control_rules (
  id, created_at, created_by, is_active, object_type, object_id, subject_type, subject_id, action_type
) (
  SELECT
    uuid_generate_v4(), now(), 'default', true, 'DATASOURCE', UUID(datasource_ids.id), 'ALL', null, 'VIEW'
  FROM
  (VALUES('866b22ee-e3e5-43e3-8ba0-d538249ab3b6'),
  ('b5d02569-11f7-473d-be82-d1d4e1ce45f3'),
  ('e8c4d923-5a73-430d-8fe4-53bd6a12ce6a'),
  ('7b205ec9-6cce-444d-998a-f34d379258b2'),
  ('a7b4a88e-4f69-477f-9784-343fb775ab12'),
  ('5a462d31-5744-4ab9-9e80-5dbcb118f72f')) as datasource_ids(id)
);

UPDATE scenes SET datasource = '866b22ee-e3e5-43e3-8ba0-d538249ab3b6' WHERE datasource IN
('02e8ffdb-d20d-4a50-9a12-23a9b3cf7f0d', 'fa85c966-aff9-4484-8e91-02bf923e86ed', '944d6e0f-8994-4251-ba11-376418bde6e3',
 '5ea52134-ab8e-4dd4-93bb-461bdac9dcaa', '301a3a93-7110-4239-ac1b-4a15e68bcd56', 'fa364cbb-c742-401e-8815-d69d0f042382');

UPDATE uploads SET datasource = '866b22ee-e3e5-43e3-8ba0-d538249ab3b6' WHERE datasource IN
('02e8ffdb-d20d-4a50-9a12-23a9b3cf7f0d', 'fa85c966-aff9-4484-8e91-02bf923e86ed', '944d6e0f-8994-4251-ba11-376418bde6e3',
 '5ea52134-ab8e-4dd4-93bb-461bdac9dcaa', '301a3a93-7110-4239-ac1b-4a15e68bcd56', 'fa364cbb-c742-401e-8815-d69d0f042382');

DELETE FROM datasources WHERE id in
('02e8ffdb-d20d-4a50-9a12-23a9b3cf7f0d', 'fa85c966-aff9-4484-8e91-02bf923e86ed', '944d6e0f-8994-4251-ba11-376418bde6e3',
 '5ea52134-ab8e-4dd4-93bb-461bdac9dcaa', '301a3a93-7110-4239-ac1b-4a15e68bcd56', 'fa364cbb-c742-401e-8815-d69d0f042382');
"""
    ))
}
