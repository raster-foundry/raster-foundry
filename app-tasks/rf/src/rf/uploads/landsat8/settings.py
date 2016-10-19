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
resolution_band_lookup = {
    '15m': [8],
    '30m': [1, 2, 3, 4, 5, 6, 7, 9, 10, 11]
}

usgs_landsat_url = (
    'http://landsat.usgs.gov/metadata_service/bulk_metadata_files/LANDSAT_8.csv'
)

aws_landsat_base = 'http://landsat-pds.s3.amazonaws.com/'

bucket_name = 'landsat-pds'
bucket = s3.Bucket(bucket_name)
