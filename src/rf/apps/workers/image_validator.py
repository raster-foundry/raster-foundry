# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import boto3
from osgeo import gdal, osr


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

        # Convert coords to WGS84
        old_cs = osr.SpatialReference()
        old_cs.ImportFromWkt(data.GetProjectionRef())
        wgs84_wkt = """
        GEOGCS["WGS 84",
            DATUM["WGS_1984",
                SPHEROID["WGS 84",6378137,298.257223563,
                    AUTHORITY["EPSG","7030"]],
                AUTHORITY["EPSG","6326"]],
            PRIMEM["Greenwich",0,
                AUTHORITY["EPSG","8901"]],
            UNIT["degree",0.01745329251994328,
                AUTHORITY["EPSG","9122"]],
            AUTHORITY["EPSG","4326"]]"""
        new_cs = osr.SpatialReference()
        new_cs.ImportFromWkt(wgs84_wkt)
        transform = osr.CoordinateTransformation(old_cs, new_cs)
        mins = transform.TransformPoint(min_x, min_y)
        maxes = transform.TransformPoint(max_x, max_y)
        return [mins[0], maxes[0], mins[1], maxes[1]]
