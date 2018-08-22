"""Utilities for transforming public scenes into COGs"""

from rf.uploads.landsat8.io import get_tempdir
from rf.utils.io import s3_bucket_and_key_from_url

import boto3

import logging
from multiprocessing import Pool
import os
import subprocess

DATA_BUCKET = os.getenv('DATA_BUCKET')

s3client = boto3.client('s3')
logger = logging.getLogger(__name__)


def create_cog(image_locations, scene):
    with get_tempdir() as local_dir:
        dsts = [os.path.join(local_dir, fname) for _, fname in image_locations]
        fetch_imagery(image_locations, local_dir)
        merged_tif = merge_tifs(dsts, local_dir)
        add_overviews(merged_tif)
        cog_path = convert_to_cog(merged_tif, local_dir)
        updated_scene = upload_tif(cog_path, scene)
        updated_scene.update()


def add_overviews(tif_path):
    logger.info('Adding overviews to %s', tif_path)
    overviews_command = ['gdaladdo', tif_path]
    subprocess.check_call(overviews_command)


def convert_to_cog(tif_with_overviews_path, local_dir):
    logger.info('Converting %s to a cog', tif_with_overviews_path)
    out_path = os.path.join(local_dir, 'cog.tif')
    cog_command = [
        'gdal_translate',
        tif_with_overviews_path,
        # may need to add nodata, not sure yet
        # '-a_nodata', '255'
        '-co',
        'TILED=YES',
        '-co',
        'COMPRESS=LZW',
        '-co',
        'COPY_SRC_OVERVIEWS=YES',
        out_path
    ]
    subprocess.check_call(cog_command)
    return out_path


def fetch_imagery(image_locations, local_dir):
    pool = Pool(8)
    tupled = [(loc[0], loc[1], local_dir) for loc in image_locations]
    try:
        pool.map(fetch_imagery_uncurried, tupled)
    finally:
        pool.close()


def fetch_imagery_uncurried(tup):
    """Fetch imagery using a (location, filename, output_directory) tuple

    This function exists so that something can be pickled and passed to pool.map,
    since downloads are costly, functions are only pickleable if they're
    defined at the top level of a module, and pool.map takes functions of one
    argument.

    Uncurried from:

    uncurried :: (a -> b -> c) -> (a, b) -> c

    in Haskell-land
    """

    fetch_image(tup[0], tup[1], tup[2])


def fetch_image(location, filename, local_dir):
    bucket, key = s3_bucket_and_key_from_url(location)
    if bucket.startswith('landsat'):
        extra_kwargs = {}
    elif bucket.startswith('sentinel'):
        extra_kwargs = {'RequestPayer': 'requester'}
    # both sentinel and landsat have these uris in bucket.s3.amazonaws.com/...,
    # so bucket and key from url does a bad job splitting correctly. follow up
    # by splitting on '.' and taking the first one
    bucket = bucket.split('.')[0]
    logger.info('Fetching image from bucket %s with key %s', bucket, key)
    dst = os.path.join(local_dir, filename)
    with open(dst, 'w') as outf:
        outf.write(
            s3client.get_object(Bucket=bucket, Key=key,
                                **extra_kwargs)['Body'].read())


def merge_tifs(local_tif_paths, local_dir):
    logger.info('Merging {} tif paths'.format(len(local_tif_paths)))
    logger.debug('The files are:\n%s', '\n'.join(local_tif_paths))
    merged_path = os.path.join(local_dir, 'merged.tif')
    merge_command = [
        'gdal_merge.py',
        '-o',
        merged_path,
        # may need to add nodata, not sure yet
        # '-a_nodata', '255',
        '-separate'
    ] + local_tif_paths
    subprocess.check_call(merge_command)
    return merged_path


def upload_tif(tif_path, scene):
    key = os.path.join('public-cogs', '{}_COG.tif'.format(scene.id))
    s3uri = 's3://{}/{}'.format(DATA_BUCKET, key)
    logger.info('Uploading tif to S3 at %s', s3uri)
    with open(tif_path, 'r') as inf:
        s3client.put_object(Bucket=DATA_BUCKET, Key=key, Body=inf)
    logger.info('Tif uploaded successfully')
    scene.ingestLocation = s3uri
    scene.sceneType = 'COG'
    scene.ingestStatus = 'INGESTED'
    return scene
