# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.conf import settings

import os
import uuid
from boto.s3.key import Key
from boto.s3.connection import S3Connection
from osgeo import gdal


def ensure_band_count(key_string, byte_range=None):
    """
    Gets the first `range` bytes from the s3 resource and uses it to
    determine the number of bands in the image.
    """
    connection = S3Connection()
    bucket = connection.get_bucket(settings.AWS_BUCKET_NAME)
    s3_key = Key(bucket)
    s3_key.key = key_string
    random_filename = '/tmp/' + str(uuid.uuid4())
    with open(random_filename, 'w') as tempfile:
        if byte_range is not None:
            header_range = {'Range': 'bytes=' + byte_range}
            s3_key.get_contents_to_file(tempfile, headers=header_range)
        else:
            s3_key.get_contents_to_file(tempfile)

    try:
        validator = gdal.Open(random_filename)
        # Tiler needs 3+ bands.
        raster_ok = validator.RasterCount >= 3
    except AttributeError:
        raster_ok = False

    os.remove(random_filename)
    return raster_ok
