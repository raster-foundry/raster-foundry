import slick.jdbc.PostgresProfile.api._
import com.liyaos.forklift.slick.SqlMigration

object M91 {
  RFMigrations.migrations = RFMigrations.migrations :+ SqlMigration(91)(
    List(
      sqlu"""
        UPDATE datasources SET bands = '{}';

        ALTER TABLE datasources ALTER COLUMN bands SET DEFAULT '{}';

        -- Update Landsat8 bands
        UPDATE datasources
        SET bands = '[{"name":"Coastal Aerosol","number":"1","wavelength":0.443},{"name":"Blue","number":"2","wavelength":0.482},{"name":"Green","number":"3","wavelength":0.562},{"name":"Red","number":"4","wavelength":0.655},{"name":"Near Infrared","number":"5","wavelength":0.865},{"name":"Shortwave Infrared 1","number":"6","wavelength":1.609},{"name":"Shortwave Infrared 2","number":"7","wavelength":2.201},{"name":"Panchromatic","number":"8","wavelength":0.590},{"name":"Cirrus","number":"9","wavelength":1.374},{"name":"Thermal Infrared 1","number":"10","wavelength":10.895},{"name":"Thermal Infrared 2","number":"11","wavelength":12.005}]'
        WHERE id = '697a0b91-b7a8-446e-842c-97cda155554d';

        -- Update Sentinel (2A and 2B) bands
        UPDATE datasources
        SET
          bands = '[{"name":"Coastal Aerosol","number":"1","wavelength":0.443},{"name":"Blue","number":"2","wavelength":0.490},{"name":"Green","number":"3","wavelength":0.560},{"name":"Red","number":"4","wavelength":0.665},{"name":"Red Edge 1","number":"5","wavelength":0.705},{"name":"Red Edge 2","number":"6","wavelength":0.740},{"name":"Red Edge 3","number":"7","wavelength":0.783},{"name":"Near Infrared","number": "8","wavelength":0.842},{"name":"Narrow Near Infrared","number":"8A","wavelength":0.842},{"name":"Water Vapor","number": "9" ,"wavelength":0.945},{"name":"SWIR - Cirrus","number": "10","wavelength":1.375},{"name":"SWIR 2","number": "11","wavelength":1.610},{"name":"SWIR 3","number":"12","wavelength":2.190}]',
          composites = '{
            "natural": {
                "label": "Natural Color",
                "value": {
                    "redBand": 4,
                    "greenBand": 3,
                    "blueBand": 2
                }
            },
            "nir": {
                "label": "Infrared",
                "value": {
                    "redBand": 5,
                    "greenBand": 4,
                    "blueBand": 3
                }
            },
            "swir": {
                "label": "Shortwave Infrared",
                "value": {
                    "redBand": 12,
                    "greenBand": 8,
                    "blueBand": 4
                }
            },
            "bathymetric": {
                "label": "Bathymetric",
                "value": {
                    "redBand": 4,
                    "greenBand": 3,
                    "blueBand": 1
                }
            },
            "geology": {
                "label": "Geology",
                "value": {
                    "redBand": 12,
                    "greenBand": 11,
                    "blueBand": 2
                }
            },
            "agriculture": {
                "label": "Agriculture",
                "value": {
                    "redBand": 11,
                    "greenBand": 8,
                    "blueBand": 4
                }
            }
        }'
        WHERE id = 'c33db82d-afdb-43cb-a6ac-ba899e48638d' OR id = '4a50cb75-815d-4fe5-8bc1-144729ce5b42';
    """
    ))
}
