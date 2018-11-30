import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M106 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(106)(
    List(
      sqlu"""
-- Delete old MODIS datasource
DELETE FROM datasources WHERE id = '73b24c83-1da9-4118-ae3f-ac601d1b701b';

-- MODIS - MOD09A1
INSERT INTO datasources(
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
    'a11b768b-d869-476e-a1ed-0ac3205ed761',
    now(),
    'default',
    now(),
    'default',
    'dfac6307-b5ef-43f7-beda-b9f208bb7726',
    'M0D09A1: MODIS/Terra Surface Reflectance 8-Day L3 Global 500m',
    'PUBLIC',
    '{}',
    '{
        "natural":{
            "label": "Natural Color",
            "value": {
                "redBand": 1,
                "greenBand": 4,
                "blueBand": 3
            }
        },
        "water-veg":{
            "label": "Band 3-6-7 Combination",
            "value": {
                "redBand": 3,
                "greenBand": 6,
                "blueBand": 7
            }
        },
        "burn-veg":{
            "label": "Band 7-2-1 Combination",
            "value": {
                "redBand": 7,
                "greenBand": 2,
                "blueBand": 1
            }
        }
    }',
    'default',
    '[
        {
            "name": "Red",
            "number": "1",
            "wavelength": 645.0
        }, {
            "name": "NIR",
            "number": "2",
            "wavelength": 858.0
        }, {
            "name": "Blue",
            "number": "3",
            "wavelength": 469.5
        }, {
            "name": "Green",
            "number": "4",
            "wavelength": 555.0
        }, {
            "name": "SWIR - 1",
            "number": "5",
            "wavelength": 1240.0
        }, {
            "name": "SWIR - 2",
            "number": "6",
            "wavelength": 1640.0
        }, {
            "name": "SWIR - 3",
            "number": "7",
            "wavelength": 2130.0
        }, {
            "name": "Reflectance Band Quality",
            "number": "8",
            "wavelength": null
        }, {
            "name": "Solar Zenith Angle",
            "number": "9",
            "wavelength": null
        }, {
            "name": "View Zenith Angle",
            "number": "10",
            "wavelength": null
        }, {
            "name": "Relative Azimuth Angle",
            "number": "11",
            "wavelength": null
        }, {
            "name": "State Flags",
            "number": "12",
            "wavelength": 805.0
        }, {
            "name": "Day of Year",
            "number": "13",
            "wavelength": null
        }
    ]'
);

-- MODIS - MYD09A1
INSERT INTO datasources(
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
    '55735945-9da5-47c3-8ae4-572b5e11205b',
    now(),
    'default',
    now(),
    'default',
    'dfac6307-b5ef-43f7-beda-b9f208bb7726',
    'MYD09A1: MODIS/Aqua Surface Reflectance 8-Day L3 Global 500m',
    'PUBLIC',
    '{}',
    '{
        "natural":{
            "label": "Natural Color",
            "value": {
                "redBand": 1,
                "greenBand": 4,
                "blueBand": 3
            }
        },
        "water-veg":{
            "label": "Band 3-6-7 Combination",
            "value": {
                "redBand": 3,
                "greenBand": 6,
                "blueBand": 7
            }
        },
        "burn-veg":{
            "label": "Band 7-2-1 Combination",
            "value": {
                "redBand": 7,
                "greenBand": 2,
                "blueBand": 1
            }
        }
    }',
    'default',
    '[
        {
            "name": "Red",
            "number": "1",
            "wavelength": 645.0
        }, {
            "name": "NIR",
            "number": "2",
            "wavelength": 858.0
        }, {
            "name": "Blue",
            "number": "3",
            "wavelength": 469.5
        }, {
            "name": "Green",
            "number": "4",
            "wavelength": 555.0
        }, {
            "name": "SWIR - 1",
            "number": "5",
            "wavelength": 1240.0
        }, {
            "name": "SWIR - 2",
            "number": "6",
            "wavelength": 1640.0
        }, {
            "name": "SWIR - 3",
            "number": "7",
            "wavelength": 2130.0
        }, {
            "name": "Reflectance Band Quality",
            "number": "8",
            "wavelength": null
        }, {
            "name": "Solar Zenith Angle",
            "number": "9",
            "wavelength": null
        }, {
            "name": "View Zenith Angle",
            "number": "10",
            "wavelength": null
        }, {
            "name": "Relative Azimuth Angle",
            "number": "11",
            "wavelength": null
        }, {
            "name": "State Flags",
            "number": "12",
            "wavelength": 805.0
        }, {
            "name": "Day of Year",
            "number": "13",
            "wavelength": null
        }
    ]'
);
"""
    ))
}
