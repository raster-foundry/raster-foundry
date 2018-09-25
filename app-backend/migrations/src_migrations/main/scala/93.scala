import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M93 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(93)(
    List(
      sqlu"""
        -- Update Sentinel (2A and 2B)
        UPDATE datasources
        SET
          composites = '{
            "natural": {
                "label": "Natural Color",
                "value": {
                    "redBand": 3,
                    "greenBand": 2,
                    "blueBand": 1
                }
            },
            "nir": {
                "label": "Infrared",
                "value": {
                    "redBand": 4,
                    "greenBand": 3,
                    "blueBand": 2
                }
            },
            "swir": {
                "label": "Shortwave Infrared",
                "value": {
                    "redBand": 11,
                    "greenBand": 7,
                    "blueBand": 3
                }
            },
            "bathymetric": {
                "label": "Bathymetric",
                "value": {
                    "redBand": 3,
                    "greenBand": 2,
                    "blueBand": 0
                }
            },
            "geology": {
                "label": "Geology",
                "value": {
                    "redBand": 11,
                    "greenBand": 10,
                    "blueBand": 1
                }
            },
            "agriculture": {
                "label": "Agriculture",
                "value": {
                    "redBand": 10,
                    "greenBand": 7,
                    "blueBand": 3
                }
            }
        }'
        WHERE id = 'c33db82d-afdb-43cb-a6ac-ba899e48638d' OR id = '4a50cb75-815d-4fe5-8bc1-144729ce5b42';

        -- Update RapidEye
        UPDATE datasources
        SET composites = '{
            "natural":{
                "label": "Natural Color",
                "value": {
                    "redBand": 2,
                    "greenBand": 1,
                    "blueBand": 0
                }
            }
        }'
        WHERE id = 'dd68e7eb-4055-4657-9cfb-bd82c0904f78';

        -- Update WorldView 1
        UPDATE datasources
        SET composites = '{
            "default": {
                "label": "Default",
                "value": {
                    "redBand": 0,
                    "greenBand": 0,
                    "blueBand": 0
                }
            }
        }'
        WHERE id = '121e792e-5157-47ee-b0bd-9d268880736e';

        -- Update WorldView 2
        UPDATE datasources
        SET composites = '{
            "natural": {
                "label": "Natural Color",
                "value": {
                    "redBand": 5,
                    "greenBand": 3,
                    "blueBand": 2
                }
            },
            "infrared": {
                "label": "Near Infrared and Green",
                "value": {
                    "redBand": 8,
                    "greenBand": 7,
                    "blueBand": 3
                }
            }
        }'
        WHERE id = 'e04c4583-b11e-4780-b06d-d50988f8d3f2';

        -- Update WorldView 3
        UPDATE datasources
        SET composites = '{
            "natural": {
                "label": "Natural Color",
                "value": {
                    "redBand": 5,
                    "greenBand": 4,
                    "blueBand": 2
                }
            },
            "infrared": {
                "label": "Near Infrared and Green",
                "value": {
                    "redBand": 8,
                    "greenBand": 7,
                    "blueBand": 3
                }
            }
        }'
        WHERE id = '0697a26d-68ef-4b36-8082-6c5a68ad8b1c';

        -- Update MODIS
        UPDATE datasources
        SET composites = '{
            "natural": {
                "label": "Natural Color",
                "value": {
                    "redBand": 0,
                    "greenBand": 3,
                    "blueBand": 2
                }
            }
        }'
        WHERE id = '73b24c83-1da9-4118-ae3f-ac601d1b701b';

        -- Update QuickBird
        UPDATE datasources
        SET composites = '{
            "natural": {
                "label": "Natural Color",
                "value": {
                    "redBand": 3,
                    "greenBand": 2,
                    "blueBand": 1
                }
            }
        }'
        WHERE id = '9fd7fcf3-6175-4f29-b1b2-29b82fa84321';

        -- Update Landsat 1, 2, 3
        UPDATE datasources
        SET composites = '{
            "default": {
                "label": "Default",
                "value": {
                    "redBand": 3,
                    "greenBand": 1,
                    "blueBand": 0
                }
            }
        }'
        WHERE id = '02e8ffdb-d20d-4a50-9a12-23a9b3cf7f0d'
            OR id = 'fa85c966-aff9-4484-8e91-02bf923e86ed'
            OR id = '944d6e0f-8994-4251-ba11-376418bde6e3';

        -- Update Landsat 4, 5
        UPDATE datasources
        SET composites = '{
            "natural": {
                "label": "Natural Color",
                "value": {
                    "redBand": 2,
                    "greenBand": 1,
                    "blueBand": 0
                }
            }
        }'
        WHERE id = '5ea52134-ab8e-4dd4-93bb-461bdac9dcaa'
            OR id = '301a3a93-7110-4239-ac1b-4a15e68bcd56';

        -- Update Landsat 7
        UPDATE datasources
        SET composites = '{
            "natural": {
                "label": "Natural Color",
                "value": {
                    "redBand": 2,
                    "greenBand": 1,
                    "blueBand": 0
                }
            }
        }'
        WHERE id = 'fa364cbb-c742-401e-8815-d69d0f042382';

        -- Update IKONOS-2
        UPDATE datasources
        SET composites = '{
            "natural": {
                "label": "Natural Color",
                "value": {
                    "redBand": 3,
                    "greenBand": 2,
                    "blueBand": 1
                }
            }
        }'
        WHERE id = '2c6cde7e-3d69-41f2-b697-f58d1b09276c';

        -- Update GeoEye 1, 2
        UPDATE datasources
        SET composites = '{
            "natural": {
                "label": "Natural Color",
                "value": {
                    "redBand": 3,
                    "greenBand": 2,
                    "blueBand": 1
                }
            }
        }'
        WHERE id = 'e068461d-a368-4def-92ae-e4c8cc8cb784'
            OR id = '2ee9dce4-8077-41d1-b37a-cf45a458ae3b';
    """
    ))
}
