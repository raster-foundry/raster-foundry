"""Utilities for processing geotiff uploads"""
import logging
import os
import subprocess

logger = logging.getLogger(__name__)


def convert_to_cog(prefix, fname):
    logger.info('Preparing to create geotiffs')

    cog_path = os.path.join(prefix, '{}_COG.tif'.format(
        os.path.splitext(fname)[0]))

    # Tile the source tif
    translate_cmd = [
        'gdal_translate',
        os.path.join(prefix, fname),
        os.path.join(prefix, 'translated.tif'), '-co', 'TILED=YES', '-co',
        'COMPRESS=DEFLATE', '-co', 'PREDICTOR=2'
    ]

    # Add overviews to the tiled tif
    overviews_cmd = [
        'gdaladdo', '-r', 'average',
        '--config', 'COMPRESS_OVERVIEW', 'DEFLATE',
        os.path.join(prefix, 'translated.tif')
    ]

    # convert the tif with overviews to a COG
    cog_cmd = [
        'gdal_translate',
        os.path.join(prefix, 'translated.tif'), cog_path, '-co', 'TILED=YES',
        '-co', 'COMPRESS=DEFLATE', '-co', 'COPY_SRC_OVERVIEWS=YES'
    ]

    logger.info('Tiling input tif')
    subprocess.check_call(translate_cmd)

    logger.info('Adding overviews')
    subprocess.check_call(overviews_cmd)

    logger.info('Converting tif to COG')
    subprocess.check_call(cog_cmd)

    return cog_path
