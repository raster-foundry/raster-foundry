import os
import subprocess

import boto3
from botocore.exceptions import ClientError

import rf.uploads.geotiff.io as geotiff_io
from rf.utils.io import s3_obj_exists
from rf.ingest import geotiff_ingest
from rf.models import Image, Scene

from .models import Ingest, Layer, Source

layer_s3_bucket = os.getenv('TILE_SERVER_BUCKET')


def process_jp2000(scene_id, jp2_source):
    """Converts a Jpeg 2000 file to a tif

    Args:
        scene_id (str): scene the image is associated with
        jp2_source (str): url to a jpeg 2000 file

    Return:
        str: s3 url to the converted tif
    """

    s3client = boto3.client('s3')
    in_bucket, in_key = geotiff_io.s3_bucket_and_key_from_url(jp2_source)
    in_bucket = in_bucket.replace(r'.s3.amazonaws.com', '')
    fname_part = os.path.split(in_key)[-1]
    out_bucket = os.getenv('DATA_BUCKET')
    out_key = os.path.join('sentinel-2-tifs',
                           scene_id,
                           fname_part.replace('.jp2', '.tif'))
    jp2_fname = os.path.join('/tmp', fname_part)
    tif_fname = jp2_fname.replace('.jp2', '.tif')
    # Explicitly setting nbits is necessary because geotrellis only likes
    # powers of 2, and for some reason the value on the jpeg 2000 files
    # after translation is 15
    cmd = ['gdal_translate',
           '-a_nodata', '0', # set 0 to nodata value
           '-co', 'NBITS=16', # explicitly set nbits = 16
           jp2_fname,
           tif_fname]

    dst_url = geotiff_io.s3_url(out_bucket, out_key)

    # check if the object is already there
    try:
        s3client.head_object(Bucket=out_bucket, Key=out_key)
        processed = True
    except ClientError:
        processed = False

    # If the object is already there, we've converted this scene
    # before
    if not processed:
        # Download the original jp2000 file
        with open(jp2_fname, 'wb') as src:
            body = s3client.get_object(
                Bucket=in_bucket,
                Key=in_key
            )['Body']
            src.write(body.read())

        # Translate the original file and add 0 as a nodata value
        subprocess.check_call(cmd)

        # Upload the converted tif
        with open(tif_fname, 'r') as dst:
            s3client.put_object(
                Bucket=out_bucket,
                Key=out_key,
                Body=dst
            )

    # Return the s3 url to the converted image
    return dst_url


def make_tif_image_copy(image):
    """Translate an images jp2 file to tif and return a copy

    Args:
        image (Image): image to copy data from

    Return:
        Image: the image copy
    """

    copied = Image.from_dict(image.to_dict())
    copied.sourceUri = process_jp2000(image.scene, image.sourceUri)
    return copied


def copy_scene(scene):
    """Create a copy of this scene with images converted to tifs in s3

    Args:
        scene (Scene): scene to copy images from

    Return:
        Scene: the copied scene with substitute images
    """

    copied = Scene.from_dict(scene.to_dict())
    copied.images = [make_tif_image_copy(image) for image in scene.images]
    return copied


def get_source(image, extent):
    """Construct source for a Sentinel-2 image
    Args:
        image (Image): the image to construct a source for
        extent (list[int]): scene extent of the scene this image belongs to

    Return:
        Source
    """
    uri = image.sourceUri
    # Sentinel-2 is split into one tif per band
    band = image.bands[0]
    # Band names are in the template "red - 4"
    band_maps = [
        {'source': 1,
         'target': {'name': band.name,
                    'index': get_band_index(band.name)}}
    ]
    cell_size = {'width': image.resolutionMeters,
                 'height': image.resolutionMeters}
    return Source(uri, band_maps, cell_size)


def get_band_index(band_name):
    """Get the write-index value for a Sentinel-2 image band

    For bands 1 through 8, we return the band number. For 8A,
    we return 9. For bands above 8A, we add one to the band
    number.

    Args:
        band_name (str): the name of the band, e.g. "nir - 8A"

    Return:
        int
    """

    name, num = band_name.split(' - ')
    if num.lower() == '8a':
        return 9
    elif num.lower() > '8a':
        return int(num) + 1
    else:
        return int(num)


def get_sentinel2_layer(scene):
    """Construct layer for Sentinel-2 scene

    Args:
        scene (Scene): Sentinel-2 scene to construct layer 4

    Returns:
        Layer
    """

    extent = scene.get_extent()
    output_uri = 's3://{}/layers'.format(layer_s3_bucket)
    sources = [get_source(image, extent) for image in scene.images]
    cell_size = {'width': 30, 'height': 30}
    return Layer(scene.id, output_uri, sources, cell_size)


def create_sentinel2_ingest(scene):
    """Translate the Sentinel 2 imagery to geotiff and create ingest definition

    Args:
        scene (Scene): scene to create an ingest definition for

    Return:
        Ingest
    """

    copied = copy_scene(scene)
    layer = get_sentinel2_layer(copied)
    id = copied.id
    return Ingest(id, [layer])
