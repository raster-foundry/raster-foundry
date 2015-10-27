# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import os
import uuid
import warnings
import logging

import boto3
from PIL import Image
from django.conf import settings

import apps.core.enums as enums
import apps.workers.status_updates as status_updates
from apps.core.models import LayerImage, Layer


log = logging.getLogger(__name__)

IMAGE_THUMB_SMALL_DIMS = (80, 80)
IMAGE_THUMB_LARGE_DIMS = (300, 300)
# LAYER_THUMB_SMALL_DIMS = (80, 80)
LAYER_THUMB_LARGE_DIMS = (400, 150)
THUMB_EXT = 'png'
THUMB_CONTENT_TYPE = 'image/png'

ERROR_MESSAGE_THUMBNAIL_FAILED = 'Thumbnail failed for image.'

# Mute warnings about processing large files.
warnings.simplefilter('ignore', Image.DecompressionBombWarning)


def make_thumb(image, thumb_width, thumb_height):
    """
    Returns a thumbnail created from image that is thumb_width x thumb_height.
    """
    # To make the thumbnail, crop the image so that it matches
    # the aspect ratio of the thumbnail. Then, scale the cropped image
    # so it has the desired dimensions.
    image_ratio = float(image.width) / float(image.height)
    thumb_ratio = float(thumb_width) / float(thumb_height)

    # If thumbnail is more oblong than the original,
    # use the full height of the original, and use a fraction of the width
    # when cropping. Otherwise, use the full width of the original, and
    # use a fraction of the height.
    if image_ratio > thumb_ratio:
        crop_height = image.height
        crop_width = crop_height * thumb_ratio
    else:
        crop_width = image.width
        crop_height = crop_width / thumb_ratio

    # In order to avoid spurious boundaries on the the thumbnails
    # of large tiff files, we crop out the central crop_proportion
    # part of the image. I'm not sure why this works. I thought that
    # maybe the cropping box was too big and was going off the edge
    # of the image, but the crop_width and crop_height never exceed the
    # dimension of the original image.
    crop_proportion = 0.9
    border_proportion = ((1.0 - crop_proportion) / 2)

    # box = (left, upper, right, lower)
    left = int(border_proportion * crop_width)
    upper = int(border_proportion * crop_height)
    right = int(left + (crop_proportion * crop_width))
    lower = int(upper + (crop_proportion * crop_height))
    box = (left, upper, right, lower)
    cropped = image.crop(box)
    thumb = cropped.resize((thumb_width, thumb_height), Image.ANTIALIAS)

    return thumb


def s3_make_thumbs(image_key, user_id, thumb_dims, thumb_ext):
    """
    Creates thumbnails based on image_key and thumb_dims, and
    stores them on S3.
    thumb_dims -- a list containing (thumb_width, thumb_height) tuples
    Returns list of thumb keys of the form <user_id>-<uuid>.<thumb_ext>
    """
    image_filepath = os.path.join(settings.TEMP_DIR, str(uuid.uuid4()))
    s3_client = boto3.client('s3')
    log.debug('Downloading %s to %s', image_key, image_filepath)
    s3_client.download_file(settings.AWS_BUCKET_NAME,
                            image_key,
                            image_filepath)

    try:
        image = Image.open(image_filepath)
    except IOError:
        log.exception('Unable to open image')
        raise ImageCouldNotOpenError()

    thumb_filenames = []
    for thumb_width, thumb_height in thumb_dims:
        thumb_uuid = str(uuid.uuid4())
        thumb_filename = '%d-%s.%s' % \
            (user_id, thumb_uuid, thumb_ext)
        thumb_filenames.append(thumb_filename)
        thumb_filepath = os.path.join(settings.TEMP_DIR, thumb_filename)

        thumb = make_thumb(image, thumb_width, thumb_height)
        thumb.save(thumb_filepath)
        s3_client.upload_file(thumb_filepath,
                              settings.AWS_BUCKET_NAME,
                              thumb_filename,
                              ExtraArgs={'ContentType': THUMB_CONTENT_TYPE})
        os.remove(thumb_filepath)

    os.remove(image_filepath)
    return thumb_filenames


def make_thumbs_for_layer(layer_id):
    """
    Make thumbs for Layer with layer_id and associated LayerImages.
    """
    layer = Layer.objects.get(id=layer_id)
    layer_images = LayerImage.objects.filter(layer_id=layer_id)
    user_id = layer.user.id

    for image in layer_images:
        thumb_dims = [IMAGE_THUMB_SMALL_DIMS, IMAGE_THUMB_LARGE_DIMS]
        try:
            image = status_updates.update_layerimage_status_start(
                image, enums.STATUS_THUMBNAIL)
            image.thumb_small_key, image.thumb_large_key = \
                s3_make_thumbs(image.get_s3_key(), user_id,
                               thumb_dims, THUMB_EXT)
        except ImageCouldNotOpenError:
            image = status_updates.update_layerimage_status_end(
                image, enums.STATUS_THUMBNAIL, ERROR_MESSAGE_THUMBNAIL_FAILED)
            return False

        image = status_updates.update_layerimage_status_end(
            image, enums.STATUS_THUMBNAIL)
        image.save()

    # Create thumbnails for the Layer as a whole
    # using thumbnails from the final image.
    layer.thumb_small_key = image.thumb_small_key
    thumb_dims = [LAYER_THUMB_LARGE_DIMS]
    try:
        layer.thumb_large_key, = s3_make_thumbs(image.get_s3_key(),
                                                user_id, thumb_dims,
                                                THUMB_EXT)
    except ImageCouldNotOpenError:
        return False

    return True


class ImageCouldNotOpenError(ValueError):
    """
    Raise when a ValueError occurs trying to open an image.
    """
    pass
