# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import json
import logging
import os
import os.path
import subprocess
import uuid
import warnings

from contextlib import contextmanager

import boto3
import PIL.ImageOps
from PIL import Image
from django.conf import settings

from apps.workers.image_metadata import get_image_exif_data
from apps.core.models import LayerImage, Layer


log = logging.getLogger(__name__)

IMAGE_THUMB_SMALL_DIMS = (80, 80)
IMAGE_THUMB_LARGE_DIMS = (300, 300)
LAYER_THUMB_SMALL_DIMS = (80, 80)
LAYER_THUMB_LARGE_DIMS = (400, 150)

ERROR_MESSAGE_THUMBNAIL_FAILED = 'Thumbnail failed for image.'

# Mute warnings about processing large files.
warnings.simplefilter('ignore', Image.DecompressionBombWarning)


def thumbnail(src_path, dst_path, width, height):
    """
    Generate a thumbnail image.
    """
    image = Image.open(src_path)
    thumb = PIL.ImageOps.fit(
        image, (width, height), method=PIL.Image.ANTIALIAS)
    thumb.save(dst_path)


def to_png(src_path):
    """
    Use `gdal_translate` to convert source image to PNG.
    """
    dst_path = '{}.png'.format(src_path)
    # Has the thumbnail been generated already?
    if os.path.isfile(dst_path):
        return
    subprocess.call(['gdal_translate', src_path, dst_path, '-of', 'PNG'])
    return dst_path


def create_and_upload_thumbnail(image_key, image_path, dimensions):
    """
    Creates thumbnails and stores them on S3.
    Return S3 key of generated thumbnail.
    """
    width, height = dimensions
    thumb_filename = '{}_{}x{}.png'.format(image_key, width, height)
    thumb_path = os.path.join(settings.TEMP_DIR, thumb_filename)

    thumbnail(image_path, thumb_path, width, height)
    upload(thumb_path, thumb_filename)
    os.remove(thumb_path)

    return thumb_filename


@contextmanager
def downloaded_file(s3_key):
    """
    Yield downloaded image from S3 and delete when finished.
    """
    client = boto3.client('s3')
    dst_path = os.path.join(settings.TEMP_DIR, str(uuid.uuid4()))
    log.debug('Downloading %s to %s...', s3_key, dst_path)
    client.download_file(settings.AWS_BUCKET_NAME, s3_key, dst_path)
    yield dst_path
    os.remove(dst_path)


def upload(src_path, s3_key):
    """
    Upload image to S3 (assumes PNG).
    """
    client = boto3.client('s3')
    log.debug('Uploading %s to %s...', src_path, s3_key)
    client.upload_file(src_path,
                       settings.AWS_BUCKET_NAME,
                       s3_key,
                       ExtraArgs={'ContentType': 'image/png'})


def thumbnail_layer(layer_id):
    """
    Generate layer thumbnails.
    """
    # Use first image to generate layer thumbnail (for now).
    image = LayerImage.objects.filter(layer_id=layer_id).first()
    image_key = image.get_s3_key()

    with downloaded_file(image_key) as image_path:
        png_path = to_png(image_path)

        thumb_small_key = create_and_upload_thumbnail(
            image_key, png_path, LAYER_THUMB_SMALL_DIMS)
        thumb_large_key = create_and_upload_thumbnail(
            image_key, png_path, LAYER_THUMB_LARGE_DIMS)

        Layer.objects.filter(id=layer_id).update(
            thumb_small_key=thumb_small_key,
            thumb_large_key=thumb_large_key)


def thumbnail_image(image_id):
    """
    Generate image thumbnails.
    """
    image = LayerImage.objects.get(id=image_id)
    image_key = image.get_s3_key()

    with downloaded_file(image_key) as image_path:
        save_exif_data(image, image_path)
        png_path = to_png(image_path)

        thumb_small_key = create_and_upload_thumbnail(
            image_key, png_path, IMAGE_THUMB_SMALL_DIMS)
        thumb_large_key = create_and_upload_thumbnail(
            image_key, png_path, IMAGE_THUMB_LARGE_DIMS)

        LayerImage.objects.filter(id=image_id).update(
            thumb_small_key=thumb_small_key,
            thumb_large_key=thumb_large_key)


def save_exif_data(image, path):
    log.debug('Saving EXIF data')
    with open(path) as fp:
        exif_data = get_image_exif_data(fp)
        meta_json = json.dumps(exif_data)
        LayerImage.objects.filter(id=image.id).update(
            meta_json=meta_json)
