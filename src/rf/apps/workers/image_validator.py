# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.conf import settings

import imghdr
import os
import uuid

from boto.s3.key import Key
from boto.s3.connection import S3Connection
from boto.exception import NoAuthHandlerFound, S3ResponseError
from osgeo import gdal

SUPPORTED_MIMES = [  # See https://docs.python.org/2/library/imghdr.html
    'tiff',
]

ERROR_MESSAGE_UNSUPPORTED_MIME = 'The image format is not supported.' + \
                                 ' Supported formats are: ' + \
                                 ', '.join(SUPPORTED_MIMES)
ERROR_MESSAGE_GENERIC_UNKNOWN = 'The system encountered an error.' + \
                                ' Please try again later.'
ERROR_MESSAGE_IMAGE_NOT_VALID = 'Image was not a valid GeoTiff file.'
ERROR_MESSAGE_IMAGE_TOO_FEW_BANDS = 'Image does not have enough band data.'


class ImageValidator(object):
    def __init__(self, key_string, byte_range=None):
        self.error = None
        self.random_filename = None
        try:
            connection = S3Connection()
            bucket = connection.get_bucket(settings.AWS_BUCKET_NAME)
            s3_key = Key(bucket)
            s3_key.key = key_string
            self.random_filename = os.path.join(settings.TEMP_DIR, str(uuid.uuid4()))
            with open(self.random_filename, 'w') as tempfile:
                if byte_range is not None:
                    header_range = {'Range': 'bytes=' + byte_range}
                    s3_key.get_contents_to_file(tempfile, headers=header_range)
                else:
                    s3_key.get_contents_to_file(tempfile)
        except (NoAuthHandlerFound, S3ResponseError):
            self.random_filename = None
            # Set an error but don't let the user know anything scary.
            self.error = ERROR_MESSAGE_GENERIC_UNKNOWN

    def __del__(self):
        try:
            os.remove(self.random_filename)
        except OSError:
            # Could not remove the file. Shouldn't be a problem though since
            # it is ephemeral.
            pass

    def image_has_min_bands(self, band_count):
        """
        Gets the first `range` bytes from the s3 resource and uses it to
        determine the number of bands in the image.
        """
        # Fail fast in case we couldn't get the file.
        if not self.random_filename:
            return False

        try:
            validator = gdal.Open(self.random_filename)
            # Tiler needs 3+ bands.
            raster_ok = validator.RasterCount >= band_count
            if not raster_ok:
                self.error = ERROR_MESSAGE_IMAGE_TOO_FEW_BANDS + \
                             ' A minimum of ' + str(band_count) + \
                             ' bands are needed for processing.'
        except AttributeError:
            self.error = ERROR_MESSAGE_IMAGE_NOT_VALID
            raster_ok = False

        return raster_ok

    def image_format_is_supported(self):
        # Fail fast in case we couldn't get the file.
        if not self.random_filename:
            return False

        supported = imghdr.what(self.random_filename) in SUPPORTED_MIMES
        if not supported:
            self.error = ERROR_MESSAGE_UNSUPPORTED_MIME

        return supported

    def get_error(self):
        return self.error;
