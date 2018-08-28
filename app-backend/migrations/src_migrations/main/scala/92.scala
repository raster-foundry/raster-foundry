import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M92 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(92)(
    List(
      sqlu"""
-- RapidEye
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
    'dd68e7eb-4055-4657-9cfb-bd82c0904f78',
    now(),
    'default',
    now(),
    'default',
    'dfac6307-b5ef-43f7-beda-b9f208bb7726',
    'RapidEye',
    'PUBLIC',
    '{}',
    '{
        "natural":{
            "label": "Natural Color",
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
            "number": "1",
            "wavelength": 475.0
        }, {
            "name": "Green",
            "number": "2",
            "wavelength": 555.0
        }, {
            "name": "Red",
            "number": "3",
            "wavelength": 657.5
        }, {
            "name": "Red Edge",
            "number": "4",
            "wavelength": 710.0
        }, {
            "name": "Near Infrared",
            "number": "5",
            "wavelength": 805.0
        }
    ]'
);

-- WorldView 1
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
    '121e792e-5157-47ee-b0bd-9d268880736e',
    now(),
    'default',
    now(),
    'default',
    'dfac6307-b5ef-43f7-beda-b9f208bb7726',
    'WorldView 1',
    'PUBLIC',
    '{}',
    '{
        "default": {
            "label": "Default",
            "value": {
                "redBand": 1,
                "greenBand": 1,
                "blueBand": 1
            }
        }
    }',
    'default',
    '[
        {
            "name": "Panchromatic",
            "number": "1",
            "wavelength": 650.0
        }
    ]'
);

-- WorldView 2
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
    'e04c4583-b11e-4780-b06d-d50988f8d3f2',
    now(),
    'default',
    now(),
    'default',
    'dfac6307-b5ef-43f7-beda-b9f208bb7726',
    'WorldView 2',
    'PUBLIC',
    '{}',
    '{
        "natural": {
            "label": "Natural Color",
            "value": {
                "redBand": 6,
                "greenBand": 4,
                "blueBand": 3
            }
        },
        "infrared": {
            "label": "Near Infrared and Green",
            "value": {
                "redBand": 9,
                "greenBand": 8,
                "blueBand": 4
            }
        }
    }',
    'default',
    '[
        {
            "name": "Panchromatic",
            "number": "1",
            "wavelength": 625.0
        }, {
            "name": "Coastal",
            "number": "2",
            "wavelength": 425.0
        }, {
            "name": "Blue",
            "number": "3",
            "wavelength": 480.0
        }, {
            "name": "Green",
            "number": "4",
            "wavelength": 560.0
        }, {
            "name": "Yellow",
            "number": "5",
            "wavelength": 605.0
        }, {
            "name": "Red",
            "number": "6",
            "wavelength": 660.0
        }, {
            "name": "Red Edge",
            "number": "7",
            "wavelength": 725.0
        }, {
            "name": "Near Infrared 1",
            "number": "8",
            "wavelength": 810.0
        }, {
            "name": "Near Infrared 2",
            "number": "9",
            "wavelength": 950.0
        }
    ]'
);

-- WorldView 3
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
    '0697a26d-68ef-4b36-8082-6c5a68ad8b1c',
    now(),
    'default',
    now(),
    'default',
    'dfac6307-b5ef-43f7-beda-b9f208bb7726',
    'WorldView 3',
    'PUBLIC',
    '{}',
    '{
        "natural": {
            "label": "Natural Color",
            "value": {
                "redBand": 6,
                "greenBand": 4,
                "blueBand": 3
            }
        },
        "infrared": {
            "label": "Near Infrared and Green",
            "value": {
                "redBand": 9,
                "greenBand": 8,
                "blueBand": 4
            }
        }
    }',
    'default',
    '[
        {
            "name": "Panchromatic",
            "number": "1",
            "wavelength": 625.0
        }, {
            "name": "Coastal",
            "number": "2",
            "wavelength": 425.0
        }, {
            "name": "Blue",
            "number": "3",
            "wavelength": 480.0
        }, {
            "name": "Green",
            "number": "4",
            "wavelength": 545.0
        }, {
            "name": "Yellow",
            "number": "5",
            "wavelength": 605.0
        }, {
            "name": "Red",
            "number": "6",
            "wavelength": 660.0
        }, {
            "name": "Red Edge",
            "number": "7",
            "wavelength": 725.0
        }, {
            "name": "Near Infrared - 1",
            "number": "8",
            "wavelength": 832.5
        }, {
            "name": "Near Infrared - 2",
            "number": "9",
            "wavelength": 950.0
        }, {
            "name": "Shortwave Infrared - 1",
            "number": "10",
            "wavelength": 1210.0
        }, {
            "name": "Shortwave Infrared - 2",
            "number": "11",
            "wavelength": 1570.0
        }, {
            "name": "Shortwave Infrared - 3",
            "number": "12",
            "wavelength": 1660.0
        }, {
            "name": "Shortwave Infrared - 4",
            "number": "13",
            "wavelength": 1730.0
        }, {
            "name": "Shortwave Infrared - 5",
            "number": "14",
            "wavelength": 2165.0
        }, {
            "name": "Shortwave Infrared - 6",
            "number": "15",
            "wavelength": 2205.0
        }, {
            "name": "Shortwave Infrared - 7",
            "number": "16",
            "wavelength": 2260.0
        }, {
            "name": "Shortwave Infrared - 8",
            "number": "17",
            "wavelength": 2330.0
        }, {
            "name": "Desert Clouds",
            "number": "18",
            "wavelength": 412.5
        }, {
            "name": "Aerosol - 1",
            "number": "19",
            "wavelength": 484.0
        }, {
            "name": "Green",
            "number": "20",
            "wavelength": 555.0
        }, {
            "name": "Aerosol - 2",
            "number": "21",
            "wavelength": 660.0
        }, {
            "name": "Water - 1",
            "number": "22",
            "wavelength": 865.0
        }, {
            "name": "Water - 2",
            "number": "23",
            "wavelength": 912.0
        }, {
            "name": "Water - 3",
            "number": "24",
            "wavelength": 947.5
        }, {
            "name": "NDVI - SWIR",
            "number": "25",
            "wavelength": 1236.0
        }, {
            "name": "Cirrus",
            "number": "26",
            "wavelength": 1385.0
        }, {
            "name": "Snow",
            "number": "27",
            "wavelength": 1650.0
        }, {
            "name": "Aerosol - 3",
            "number": "28",
            "wavelength": 2175.0
        }, {
            "name": "Aerosol - 3",
            "number": "29",
            "wavelength": 2175.0
        }
    ]'
);

-- MODIS
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
    '73b24c83-1da9-4118-ae3f-ac601d1b701b',
    now(),
    'default',
    now(),
    'default',
    'dfac6307-b5ef-43f7-beda-b9f208bb7726',
    'MODIS',
    'PUBLIC',
    '{}',
    '{
        "natural": {
            "label": "Natural Color",
            "value": {
                "redBand": 1,
                "greenBand": 4,
                "blueBand": 3
            }
        }
    }',
    'default',
    '[
        {
            "number": "1",
            "wavelength": 645
        }, {
            "number": "2",
            "wavelength": 858.5
        }, {
            "number": "3",
            "wavelength": 469.0
        }, {
            "number": "4",
            "wavelength": 555.0
        }, {
            "number": "5",
            "wavelength": 1240
        }, {
            "number": "6",
            "wavelength": 1640.0
        }, {
            "number": "7",
            "wavelength": 2130.0
        }, {
            "number": "8",
            "wavelength": 412.5
        }, {
            "number": "9",
            "wavelength": 443.0
        }, {
            "number": "10",
            "wavelength": 488.0
        }, {
            "number": "11",
            "wavelength": 531.0
        }, {
            "number": "12",
            "wavelength": 551.0
        }, {
            "number": "13",
            "wavelength": 667.0
        }, {
            "number": "14",
            "wavelength": 678.0
        }, {
            "number": "15",
            "wavelength": 748.0
        }, {
            "number": "16",
            "wavelength": 869.5
        }, {
            "number": "17",
            "wavelength": 905.0
        }, {
            "number": "18",
            "wavelength": 936.0
        }, {
            "number": "19",
            "wavelength": 940.0
        }, {
            "number": "20",
            "wavelength": 3750.0
        }, {
            "number": "21",
            "wavelength": 3959.0
        }, {
            "number": "22",
            "wavelength": 3959.0
        }, {
            "number": "23",
            "wavelength": 4050.0
        }, {
            "number": "24",
            "wavelength": 4498.0
        }, {
            "number": "25",
            "wavelength": 4515.5
        }, {
            "number": "26",
            "wavelength": 1375.0
        }, {
            "number": "27",
            "wavelength": 6715.0
        }, {
            "number": "28",
            "wavelength": 7325.0
        }, {
            "number": "29",
            "wavelength": 8550.0
        }, {
            "number": "30",
            "wavelength": 9730.0
        }, {
            "number": "31",
            "wavelength": 11030
        }, {
            "number": "32",
            "wavelength": 12020.0
        }, {
            "number": "33",
            "wavelength": 13335.0
        }, {
            "number": "34",
            "wavelength": 13635.0
        }, {
            "number": "35",
            "wavelength": 13935.0
        }, {
            "number": "36",
            "wavelength": 14235.0
        }
    ]'
);

-- Quickbird
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
    '9fd7fcf3-6175-4f29-b1b2-29b82fa84321',
    now(),
    'default',
    now(),
    'default',
    'dfac6307-b5ef-43f7-beda-b9f208bb7726',
    'QuickBird',
    'PUBLIC',
    '{}',
    '{
        "natural": {
            "label": "Natural Color",
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
            "name": "Panchromatic",
            "number": "1",
            "wavelength": 675.0
        }, {
            "name": "Blue",
            "number": "2",
            "wavelength": 485.0
        }, {
            "name": "Green",
            "number": "3",
            "wavelength": 560.0
        }, {
            "name": "Red",
            "number": "4",
            "wavelength": 690.0
        }, {
            "name": "Near Infrared",
            "number": "5",
            "wavelength": 830.0
        }
    ]'
);

-- Landsat 1
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
    '02e8ffdb-d20d-4a50-9a12-23a9b3cf7f0d',
    now(),
    'default',
    now(),
    'default',
    'dfac6307-b5ef-43f7-beda-b9f208bb7726',
    'Landsat 1',
    'PUBLIC',
    '{}',
    '{
        "default": {
            "label": "Default",
            "value": {
                "redBand": 4,
                "greenBand": 2,
                "blueBand": 1
            }
        }
    }',
    'default',
    '[
        {
            "number": "4",
            "wavelength": 550.0
        }, {
            "number": "5",
            "wavelength": 650.0
        }, {
            "number": "6",
            "wavelength": 750.0
        }, {
            "number": "7",
            "wavelength": 950.0
        }
    ]'
);

-- Landsat 2
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
    'fa85c966-aff9-4484-8e91-02bf923e86ed',
    now(),
    'default',
    now(),
    'default',
    'dfac6307-b5ef-43f7-beda-b9f208bb7726',
    'Landsat 2',
    'PUBLIC',
    '{}',
    '{
        "default": {
            "label": "Default",
            "value": {
                "redBand": 4,
                "greenBand": 2,
                "blueBand": 1
            }
        }
    }',
    'default',
    '[
        {
            "number": "4",
            "wavelength": 550.0
        }, {
            "number": "5",
            "wavelength": 650.0
        }, {
            "number": "6",
            "wavelength": 750.0
        }, {
            "number": "7",
            "wavelength": 950.0
        }
    ]'
);

-- Landsat 3
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
    '944d6e0f-8994-4251-ba11-376418bde6e3',
    now(),
    'default',
    now(),
    'default',
    'dfac6307-b5ef-43f7-beda-b9f208bb7726',
    'Landsat 3',
    'PUBLIC',
    '{}',
    '{
        "default": {
            "label": "Default",
            "value": {
                "redBand": 4,
                "greenBand": 2,
                "blueBand": 1
            }
        }
    }',
    'default',
    '[
        {
            "number": "4",
            "wavelength": 550.0
        }, {
            "number": "5",
            "wavelength": 650.0
        }, {
            "number": "6",
            "wavelength": 750.0
        }, {
            "number": "7",
            "wavelength": 950.0
        }
    ]'
);

-- Landsat 4
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
    '5ea52134-ab8e-4dd4-93bb-461bdac9dcaa',
    now(),
    'default',
    now(),
    'default',
    'dfac6307-b5ef-43f7-beda-b9f208bb7726',
    'Landsat 4',
    'PUBLIC',
    '{}',
    '{
        "natural": {
            "label": "Natural Color",
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
            "number": "1",
            "wavelength": 485.0
        }, {
            "name": "Green",
            "number": "2",
            "wavelength": 560.0
        }, {
            "name": "Red",
            "number": "3",
            "wavelength": 660.0
        }, {
            "name": "Near Infrared",
            "number": "4",
            "wavelength": 830.0
        }, {
            "name": "Shortwave Infrared - 1",
            "number": "5",
            "wavelength": 1650.0
        }, {
            "name": "Thermal",
            "number": "6",
            "wavelength": 11450.0
        }, {
            "name": "Shortwave Infrared - 2",
            "number": "7",
            "wavelength": 2215.0
        }
    ]'
);

-- Landsat 5
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
    '301a3a93-7110-4239-ac1b-4a15e68bcd56',
    now(),
    'default',
    now(),
    'default',
    'dfac6307-b5ef-43f7-beda-b9f208bb7726',
    'Landsat 5',
    'PUBLIC',
    '{}',
    '{
        "natural": {
            "label": "Natural Color",
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
            "number": "1",
            "wavelength": 485.0
        }, {
            "name": "Green",
            "number": "2",
            "wavelength": 560.0
        }, {
            "name": "Red",
            "number": "3",
            "wavelength": 660.0
        }, {
            "name": "Near Infrared",
            "number": "4",
            "wavelength": 830.0
        }, {
            "name": "Shortwave Infrared - 1",
            "number": "5",
            "wavelength": 1650.0
        }, {
            "name": "Thermal",
            "number": "6",
            "wavelength": 11450.0
        }, {
            "name": "Shortwave Infrared - 2",
            "number": "7",
            "wavelength": 2215.0
        }
    ]'
);

-- Landsat 7
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
    'fa364cbb-c742-401e-8815-d69d0f042382',
    now(),
    'default',
    now(),
    'default',
    'dfac6307-b5ef-43f7-beda-b9f208bb7726',
    'Landsat 7',
    'PUBLIC',
    '{}',
    '{
        "natural": {
            "label": "Natural Color",
            "value": {
                "redBand": 1,
                "greenBand": 2,
                "blueBand": 3
            }
        }
    }',
    'default',
    '[
        {
            "name": "Blue",
            "number": "1",
            "wavelength": 485.0
        }, {
            "name": "Green",
            "number": "2",
            "wavelength": 570.0
        }, {
            "name": "Red",
            "number": "3",
            "wavelength": 660.0
        }, {
            "name": "Near Infrared",
            "number": "4",
            "wavelength": 840.0
        }, {
            "name": "Shortwave Infrared - 1",
            "number": "5",
            "wavelength": 1650.0
        }, {
            "name": "Thermal",
            "number": "6",
            "wavelength": 11450.0
        }, {
            "name": "Shortwave Infrared - 2",
            "number": "7",
            "wavelength": 2220.0
        }, {
            "name": "Panchromatic",
            "number": "8",
            "wavelength": 710.0
        }
    ]'
);

-- IKONOS-2
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
    '2c6cde7e-3d69-41f2-b697-f58d1b09276c',
    now(),
    'default',
    now(),
    'default',
    'dfac6307-b5ef-43f7-beda-b9f208bb7726',
    'IKONOS-2',
    'PUBLIC',
    '{}',
    '{
        "natural": {
            "label": "Natural Color",
            "value": {
                "redBand": 1,
                "greenBand": 4,
                "blueBand": 3
            }
        }
    }',
    'default',
    '[
        {
            "name": "Panchromatic",
            "number": "1",
            "wavelength": 675.0
        }, {
            "name": "Blue",
            "number": "2",
            "wavelength": 480.5
        }, {
            "name": "Green",
            "number": "3",
            "wavelength": 550.5
        }, {
            "name": "Red",
            "number": "4",
            "wavelength": 665.0
        }, {
            "name": "Near Infrared",
            "number": "5",
            "wavelength": 805.0
        }
    ]'
);

-- GeoEye 1
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
    'e068461d-a368-4def-92ae-e4c8cc8cb784',
    now(),
    'default',
    now(),
    'default',
    'dfac6307-b5ef-43f7-beda-b9f208bb7726',
    'GeoEye 1',
    'PUBLIC',
    '{}',
    '{
        "natural": {
            "label": "Natural Color",
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
            "name": "Panchromatic",
            "number": "1",
            "wavelength": 625.0
        }, {
            "name": "Blue",
            "number": "2",
            "wavelength": 480.0
        }, {
            "name": "Green",
            "number": "3",
            "wavelength": 545.0
        }, {
            "name": "Red",
            "number": "4",
            "wavelength": 672.5
        }, {
            "name": "Near Infrared",
            "number": "5",
            "wavelength": 850.0
        }
    ]'
);

-- GeoEye 2
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
    '2ee9dce4-8077-41d1-b37a-cf45a458ae3b',
    now(),
    'default',
    now(),
    'default',
    'dfac6307-b5ef-43f7-beda-b9f208bb7726',
    'GeoEye 2',
    'PUBLIC',
    '{}',
    '{
        "natural": {
            "label": "Natural Color",
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
            "name": "Panchromatic",
            "number": "1",
            "wavelength": 625.0
        }, {
            "name": "Blue",
            "number": "2",
            "wavelength": 480.0
        }, {
            "name": "Green",
            "number": "3",
            "wavelength": 545.0
        }, {
            "name": "Red",
            "number": "4",
            "wavelength": 672.5
        }, {
            "name": "Near Infrared",
            "number": "5",
            "wavelength": 850.0
        }
    ]'
);
    """
    ))
}
