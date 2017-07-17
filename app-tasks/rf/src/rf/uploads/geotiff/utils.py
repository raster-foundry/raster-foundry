"""Utilities for processing geotiff uploads"""
import logging
import os
import subprocess

import boto3
import rasterio

logger = logging.getLogger(__name__)


def is_tif_too_large(tif_path):
    """Simple function to check if tif is larger than 1 gb

    Args:
        tif_path (str): full filepath to tif

    Returns:
        Boolean
    """

    file_stats = os.stat(tif_path)
    with rasterio.open(tif_path) as src:
        total_bounds = src.width * src.height

    max_tif_size_bytes = 1e9
    geotrellis_max_total_bounds = 2**31 - 1
    if total_bounds > geotrellis_max_total_bounds or file_stats.st_size > max_tif_size_bytes:
        return True
    return False


def split_tif(tif_path, temp_directory, split_width_pixels=12000, split_height_pixels=12000):
    """Function to split a tif into multiple, tiled tifs based on arguments

    Args:
        tif_path (str): path to tif to split
        temp_directory (str): directory to put tiled tifs
        split_width_pixels (int): optional width (in pixels) to create tiles
        split_height_pixels (int): optional height (in pixels) to create tiles

    Returns:
        list[str]
    """
    logger.info('Created directory %s to split files', temp_directory)
    command = ['gdal_retile.py',
               '-co', 'COMPRESS=LZW',
               '-co', 'TILED=YES',
               '-overlap', '100',
               '-ps', str(split_width_pixels), str(split_height_pixels),
               '-targetDir', temp_directory,
               tif_path]

    logger.info('Splitting tif using gdal_retile.py with the following command: %s', ' '.join(command))
    subprocess.check_call(command)
    filepaths = [os.path.join(temp_directory, f) for f in os.listdir(temp_directory)]

    logger.info('Created %s new files', len(filepaths))
    return filepaths


def upload_split_files(source_key, source_bucket_name, filepaths):
    """Uploads a list of split files to S3 in sub-folder under the source-key

    If there is a key such as 'really-big-file.tif' that is split into multiple tiles,
    the tiles will be uploaded to 'really-big-file.tif-split-files/<files>'

    Returns a list[(str, str)] where the first element in the tuple is the s3 key and the second
    is the local filepath of the tif

    Args:
        source_key (str): key of file that has been split
        source_bucket_name (str): bucket that original file came from and new file will be uploaded to
        filepaths (list[str]): split files to be uploaded

    Returns:
        list[(str, str)]
    """
    splits_s3_dir = '{}-split-files/'.format(source_key)
    logger.info('Uploading split files to s3://%s/%s', source_bucket_name, splits_s3_dir)

    keys_and_filepaths = []

    for filepath in filepaths:
        filename = os.path.split(filepath)[-1]
        s3 = boto3.resource('s3')
        bucket = s3.Bucket(source_bucket_name)
        key = os.path.join(splits_s3_dir, filename)

        logger.debug('Uploading to s3 %s => s3://%s/%s', bucket, key)

        bucket.upload_file(filepath, key)
        keys_and_filepaths.append((key, filepath))

    logger.info('Finished uploading files')
    return keys_and_filepaths
