# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import boto3
from osgeo import gdal


MINIMUM_BAND_COUNT = 3


class ImageValidator(object):
    def __init__(self, bucket, key):
        self.bucket = bucket
        self.key = key

    def open_image(self):
        """
        Open file using GDAL.
        Throws RuntimeError if file is not accessible or invalid..
        """
        gdal.UseExceptions()
        client = boto3.client('s3')
        uri = client.generate_presigned_url('get_object', {
            'Bucket': self.bucket,
            'Key': self.key,
        })

        # Strip access tokens.
        # Since these objects are public the tokens are uneccesary.
        uri, _ = uri.split('?')

        uri = '/vsicurl/' + uri
        return gdal.Open(uri)

    def image_has_enough_bands(self):
        data = self.open_image()
        return data.RasterCount >= MINIMUM_BAND_COUNT

    def image_format_is_supported(self):
        data = self.open_image()
        return data.GetDriver().GetDescription().upper() == 'GTIFF'

    def get_image_bounds(self):
        data = self.open_image()
        w = data.RasterXSize
        h = data.RasterYSize

        bound_info = data.GetGeoTransform()
        min_x = bound_info[0]
        min_y = bound_info[3] + (w * bound_info[4]) + (h * bound_info[5])
        max_x = bound_info[0] + (w * bound_info[1]) + (h * bound_info[2])
        max_y = bound_info[3]
        return [min_x, max_x, min_y, max_y]
