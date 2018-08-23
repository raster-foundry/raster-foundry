import logging
import os
import subprocess

import rasterio
from rasterio.warp import calculate_default_transform, reproject, Resampling

logger = logging.getLogger(__name__)


def warp_tif(combined_tif_path, warped_tif_path, dst_crs={'init': 'EPSG:3857'}):
    with rasterio.open(combined_tif_path) as src:
        meta = src.meta
        new_meta = meta.copy()
        transform, width, height = calculate_default_transform(
            src.crs, dst_crs, src.width, src.height, *src.bounds)
        new_meta.update({
            'crs': dst_crs,
            'transform': transform,
            'width': width,
            'height': height
        })
        with rasterio.open(warped_tif_path, 'w', compress='LZW', tiled=True, **new_meta) as dst:
            for i in range(1, src.count):
                reproject(
                    source=rasterio.band(src, i),
                    destination=rasterio.band(dst, i),
                    src_transform=src.transform,
                    src_crs=src.crs,
                    dst_transform=transform,
                    dst_crs=dst_crs,
                    resampling=Resampling.nearest
                )


def create_geotiffs(modis_path, output_directory):
    """Create geotiffs from MODIS HDF

    1. Create separate tifs for each band
    2. Combine tifs into a single tif
    3. Warp tif to web mercator (for quicker access)
    4. Generate COG

    Args:
        modis_path (str): path to modis HDF file
        output_directory (str): directory to output tiffs to
    """
    logger.info('Preparing to create geotiffs')

    # Set up directories/paths for created files
    pre_warp_directory = os.path.join(output_directory, 'modis-separated')
    os.mkdir(pre_warp_directory)
    pre_warp_output_path = os.path.join(pre_warp_directory, 'B.tif')
    combined_tif_filepath = os.path.join(output_directory, 'combined-tif.tif')
    warped_tif_path = os.path.join(output_directory, 'warped-combined.tif')
    cog_filename = '.'.join(os.path.basename(modis_path).split('.')[:-1]) + '.tif'
    cog_tif_filepath = os.path.join(output_directory, cog_filename)

    # Separate out into tifs for each band
    translate_command = ['gdal_translate', '-sds', modis_path, pre_warp_output_path]

    # Combine into single tif (assign nodata value)
    merge_command = ['gdal_merge.py',
                     '-o', combined_tif_filepath,
                     '-a_nodata', '-28672',
                     '-separate']

    # Generate overviews
    overview_command = ['gdaladdo', warped_tif_path, '--config', 'INTERLEAVE_OVERVIEW=BAND',
                        '--config', 'COMPRESS_OVERVIEW=DEFLATE']

    # Create final tif with overviews
    translate_cog_command = ['gdal_translate', warped_tif_path, '-a_nodata', '-28672',
                             '-co', 'TILED=YES',
                             '-co', 'COMPRESS=LZW',
                             '-co', 'COPY_SRC_OVERVIEWS=YES',
                             '-co', 'INTERLEAVE=BAND',
                             cog_tif_filepath]

    logger.info('Creating tifs for Subdatasets: %s', ' '.join(translate_command))
    subprocess.check_call(translate_command)

    tifs = [os.path.join(pre_warp_directory, f) for f in os.listdir(pre_warp_directory)]
    # Sort so that band order is correct
    tifs.sort()

    logger.info('Running Merge Command: %s', ' '.join(merge_command + tifs))
    subprocess.check_call(merge_command + tifs)

    # Warp combined tif
    logger.info('Warping tif %s => %s', combined_tif_filepath, warped_tif_path)
    warp_tif(combined_tif_filepath, warped_tif_path)
    logger.info('Running Overview Command: %s', ' '.join(overview_command))
    subprocess.check_call(overview_command)
    logger.info('Running COG translate Command: %s', ' '.join(translate_cog_command))
    subprocess.check_call(translate_cog_command)

    return [cog_tif_filepath]
