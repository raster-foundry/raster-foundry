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
        'gdaladdo', '-r', 'average', '--config', 'COMPRESS_OVERVIEW', 'LZW',
        tif_path
    ]
    subprocess.check_call(overviews_command)


def convert_to_cog(tif_with_overviews_path, local_dir):
    logger.info('Converting %s to a cog', tif_with_overviews_path)
    out_path = os.path.join(local_dir, 'cog.tif')
    cog_command = [
        'gdal_translate', tif_with_overviews_path, '-co', 'TILED=YES', '-co',
        'COMPRESS=LZW', '-co', 'COPY_SRC_OVERVIEWS=YES', '-co', 'BIGTIFF=YES',
        '-co', 'PREDICTOR=2', out_path
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
        'gdal_merge.py', '-o', merged_path, '-separate', '-co', 'COMPRESS=LZW',
        '-co', 'PREDICTOR=2', '-co', 'BIGTIFF=YES'
    ] + local_tif_paths
    subprocess.check_call(merge_command)
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


def resample_tif(src_path, local_dir, src_x, dst_x, src_y, dst_y):
    src_fname = os.path.split(src_path)[-1]
    src_fname_ext = src_fname.split('.')[-1]
    dst_fname = src_fname.replace(src_fname_ext, 'warped.tif')
    dst_path = os.path.join(local_dir, dst_fname)
    if src_x == dst_x and src_y == dst_y:
        logger.info(
            'No need to reproject for %s, already in target resolution',
            src_path)
        subprocess.check_call(
            ['gdal_translate', '-co', 'COMPRESS=LZW', src_path, dst_path])
    # if there are any resolution difference, including if they're weird, like a
    # greater x resolution and lesser y resolution, reproject to the same size
    else:
        # Landsat 8 / Sentinel-2 images have a bunch of single band components
        x_rat = int(src_x / dst_x)
        y_rat = int(src_y / dst_y)
        logger.info('Resampling %s', src_fname)
        logger.info('Increasing x resolution by %sx, y resolution by %sx',
                    x_rat, y_rat)
        # No need to throw in -wo for the compression options, since we're doing all
        # of the bands at once
        subprocess.check_call([
            'gdalwarp', '-co', 'COMPRESS=LZW', '-co', 'PREDICTOR=2',
            '-dstnodata', '0', '-tr',
            str(dst_x),
            str(dst_y), src_path, dst_path
        ])
    return dst_path


def warp_tifs(local_tif_paths, local_dir, parallel=True):
    logger.info('Getting metadata for tifs')
    sources = [rasterio.open(p) for p in local_tif_paths]
    # a is x resoution, e is y resolution
    paths_with_resolutions = [
        (path, source.meta['transform'].a, source.meta['transform'].e)
        for path, source in zip(local_tif_paths, sources)
    ]
    # assume cells are square to find the minimum -- this could be wrong, but isn't for any
    # of the imagery we know we're using
    min_resolution = sorted(paths_with_resolutions, key=lambda x: x[1])[0]
    tupled = [(src_path, local_dir, src_x, min_resolution[1], src_y,
               min_resolution[2])
              for src_path, src_x, src_y in paths_with_resolutions]
    logger.info('Resampling to maximum available resolution')
    pool = Pool(cpu_count())
    try:
        warped_paths = pool.map(resample_tif_uncurried, tupled)
    finally:
        pool.close()
    return warped_paths


def resample_tif_uncurried(tup):
    """Resample a tif from a tuple containing all the resample_tif arguments

    The tuple is:

    (src_path, local_dir, src_x, dst_x, src_y, dst_y)

    This function exists so that something can be pickled and passed to pool.map,
    since resampling is costly, functions are only pickleable if they're
    defined at the top level of a module, and pool.map takes functions of one
    argument.

    Uncurried from:

    uncurried :: (a -> b -> c) -> (a, b) -> c

    in Haskell-land
    """

    return resample_tif(tup[0], tup[1], tup[2], tup[3], tup[4], tup[5])


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
