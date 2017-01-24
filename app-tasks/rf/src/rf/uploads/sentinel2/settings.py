"""Settings shared by functions for indexing Sentinel 2 data"""

from pyproj import Proj

from rf.utils.io import s3

# Public Organization UUID in Raster Foundry
organization = 'dfac6307-b5ef-43f7-beda-b9f208bb7726'

# Sentinel 2 bands
# Bands 2, 3, 4, and 8 are at the 10m resolution. Bands 5, 6, 7, 8a, 11, and 12
# are at the 20m resolution. Bands 1, 9, and 10 are at the 60m resolution.
# Bands are:
# 1: Coastal aerosol
# 2: Blue
# 3: Green
# 4: Red
# 5: Narrow NIR 1
# 6: Narrow NIR 2
# 7: Narrow NIR 3
# 8: NIR
# 8a: Narrow NIR 4
# 9: Water vapor
# 10: Cirrus
# 11: SWIR 1
# 12: SWIR 2

band_lookup = {
    # 10m
    'B02': {'name': 'blue - 2', 'number': 0, 'wavelength': [457, 539]},
    'B03': {'name': 'green - 3', 'number': 0, 'wavelength': [542, 578]},
    'B04': {'name': 'red - 4', 'number': 0, 'wavelength': [650, 680]},
    'B08': {'name': 'near infrared - 8', 'number': 0, 'wavelength': [784, 900]},
    # 20m
    'B05': {'name': 'near infrared - 5', 'number': 0, 'wavelength': [697, 713]},
    'B06': {'name': 'near infrared - 6', 'number': 0, 'wavelength': [732, 748]},
    'B07': {'name': 'near infrared - 7', 'number': 0, 'wavelength': [773, 793]},
    'B8A': {'name': 'near infrared - 8a', 'number': 0, 'wavelength': [855, 875]},
    'B11': {'name': 'short-wave infrared - 11', 'number': 0, 'wavelength': [1565, 1655]},
    'B12': {'name': 'short-wave infrared - 12', 'number': 0, 'wavelength': [2100, 2280]},
    # 60m
    'B01': {'name': 'coastal aerosol - 1', 'number': 0, 'wavelength': [433, 453]},
    'B09': {'name': 'water vapor - 9', 'number': 0, 'wavelength': [935, 955]},
    'B10': {'name': 'cirrus - 10', 'number': 0, 'wavelength': [1365, 1395]}
}

datasource_id = '4a50cb75-815d-4fe5-8bc1-144729ce5b42'

# Base http path for constructing resource URLs for Sentinel 2 assets
base_http_path = 'https://sentinel-s2-l1c.s3.amazonaws.com/{key_path}'

# S3/AWS settings and objects
bucket_name = 'sentinel-s2-l1c'
bucket = s3.Bucket(bucket_name)

# target projection for footprints
target_proj = Proj(init='epsg:4326')

