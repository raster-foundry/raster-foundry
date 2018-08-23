"""Settings shared by functions for indexing Landsat 8 data"""

from rf.utils.io import s3

organization = 'dfac6307-b5ef-43f7-beda-b9f208bb7726'

# Band 8 is panchromatic and at 15m resolution. All other bands
# are at the 30m resolution. Bands are:
# 1:  Coastal aerosol
# 2:  Blue
# 3:  Green
# 4:  Red
# 5:  Near infrared (NIR)
# 6:  SWIR 1
# 7:  SWIR 2
# 8:  Panchromatic
# 9:  Cirrus
# 10: Themral infrared (TIRS 1) (resampled to 30m from 100m in product)
# 11: Themral infrared (TIRS 2) (resampled to 30m from 100m in product)
#
# Source: http://landsat.usgs.gov/band_designations_landsat_satellites.php
band_lookup = {
    '15m': [{
        'name': 'panchromatic - 8',
        'number': 0,
        'wavelength': [500, 680]
    }],
    '30m': [{
        'name': 'coastal aerosol - 1',
        'number': 0,
        'wavelength': [430, 450]
    }, {
        'name': 'blue - 2',
        'number': 0,
        'wavelength': [450, 510]
    }, {
        'name': 'green - 3',
        'number': 0,
        'wavelength': [530, 590]
    }, {
        'name': 'red - 4',
        'number': 0,
        'wavelength': [640, 670]
    }, {
        'name': 'near infrared - 5',
        'number': 0,
        'wavelength': [850, 880]
    }, {
        'name': 'swir - 6',
        'number': 0,
        'wavelength': [1570, 1650]
    }, {
        'name': 'swir - 7',
        'number': 0,
        'wavelength': [2110, 2290]
    }, {
        'name': 'cirrus - 9',
        'number': 0,
        'wavelength': [1360, 1380]
    }, {
        'name': 'thermal infrared - 10',
        'number': 0,
        'wavelength': [10600, 11190]
    }, {
        'name': 'thermal infrared - 11',
        'number': 0,
        'wavelength': [11500, 12510]
    }]
}

datasource_id = '697a0b91-b7a8-446e-842c-97cda155554d'

usgs_landsat_url = (
    'https://landsat.usgs.gov/landsat/metadata_service/bulk_metadata_files/LANDSAT_8.csv'
)

aws_landsat_base = 'http://landsat-pds.s3.amazonaws.com/'

bucket_name = 'landsat-pds'
bucket = s3.Bucket(bucket_name)
