"""Utilities for transforming public scenes into COGs"""

from rf.ingest.settings import (landsat8_band_order, sentinel2_band_order,
                                landsat8_datasource_id,
                                sentinel2_datasource_id)
from rf.utils.io import s3_bucket_and_key_from_url

import boto3
import rasterio

import logging
from multiprocessing import (cpu_count, Pool)
import os
import subprocess

DATA_BUCKET = os.getenv('DATA_BUCKET')

s3client = boto3.client('s3')
logger = logging.getLogger(__name__)


def add_overviews(tif_path):
    logger.info('Adding overviews to %s', tif_path)
    overviews_command = [
        'gdaladdo', '-r', 'average', '--config', 'COMPRESS_OVERVIEW',
        'DEFLATE', tif_path
    ]
    subprocess.check_call(overviews_command)


def convert_to_cog(tif_with_overviews_path, local_dir):
    logger.info('Converting %s to a cog', tif_with_overviews_path)
    out_path = os.path.join(local_dir, 'cog.tif')
    cog_command = [
        'gdal_translate', tif_with_overviews_path, '-co', 'TILED=YES', '-co',
        'COMPRESS=DEFLATE', '-co', 'COPY_SRC_OVERVIEWS=YES', '-co',
        'BIGTIFF=IF_SAFER', '-co', 'PREDICTOR=2', out_path
    ]
    subprocess.check_call(cog_command)
    return out_path


def fetch_imagery(image_locations, local_dir):
    pool = Pool(cpu_count())
    tupled = [(loc[0], loc[1], local_dir) for loc in image_locations]
    try:
        pool.map(fetch_imagery_uncurried, tupled)
    finally:
        pool.close()


def fetch_image(location, filename, local_dir):
    bucket, key = s3_bucket_and_key_from_url(location)
    if bucket.startswith('sentinel'):
        extra_kwargs = {'RequestPayer': 'requester'}
    else:
        extra_kwargs = {}
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
    vrt_path = os.path.join(local_dir, 'merged.vrt')
    merged_path = os.path.join(local_dir, 'merged.tif')
    nodata_vals = []
    for p in local_tif_paths:
        with rasterio.open(p, 'r') as src:
            nodata_vals.append(str(int(src.meta['nodata'] or 0)))
    logger.info('Nodata vals before vrt: %s', nodata_vals)
    vrt_command = [
        'gdalbuildvrt', '-separate', '-resolution', 'highest', '-srcnodata',
        ' '.join(nodata_vals), '-vrtnodata', ' '.join(nodata_vals), vrt_path
    ] + local_tif_paths
    logger.info('VRT Command is: %s', vrt_command)
    subprocess.check_call(vrt_command)
    with rasterio.open(vrt_path, 'r') as src:
        logger.info('Nodata vals in vrt: %s', src.nodatavals)
    merge_command = [
        'gdal_translate', '-co', 'COMPRESS=DEFLATE', '-co', 'PREDICTOR=2',
        '-co', 'BIGTIFF=IF_SAFER', '-co', 'TILED=YES', vrt_path, merged_path
    ]
    subprocess.check_call(merge_command)
    with rasterio.open(merged_path, 'r') as src:
        logger.info('No data after merge: %s', src.nodatavals)
    return merged_path


def sort_key(datasource_id, band):
    if datasource_id == sentinel2_datasource_id:
        return sentinel2_band_order[band.name]
    elif datasource_id == landsat8_datasource_id:
        return landsat8_band_order[band.name]
    else:
        raise ValueError(
            'Trying to run public COG ingest for scene with mysterious datasource',
            datasource_id)


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
