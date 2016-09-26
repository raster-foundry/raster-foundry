"""Settings shared by functions for indexing Sentinel 2 data"""

import boto3

# Public Organization UUID in Raster Foundry
organization = 'dfac6307-b5ef-43f7-beda-b9f208bb7726'

# Base http path for constructing resource URLs for Sentinel 2 assets
base_http_path = 'http://sentinel-s2-l1c.s3-website.eu-central-1.amazonaws.com/{key_path}'

# S3/AWS settings and objects
s3 = boto3.resource('s3', region_name='eu-central-1')
bucket_name = 'sentinel-s2-l1c'
bucket = s3.Bucket(bucket_name)
