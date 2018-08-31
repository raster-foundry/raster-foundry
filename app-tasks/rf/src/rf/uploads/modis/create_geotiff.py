from rf.utils import cog

import glob
import logging
import os
import subprocess

import rasterio
from rasterio.warp import calculate_default_transform, reproject, Resampling

logger = logging.getLogger(__name__)


def warp_tif(combined_tif_path, warped_tif_path, dst_crs={
        'init': 'EPSG:3857'
}):
    logger.info('Warping tif to web mercator: %s', combined_tif_path)
    with rasterio.open(combined_tif_path) as src:
        meta = src.meta
        new_meta = meta.copy()
        transform, width, height = calculate_default_transform(
            src.crs, dst_crs, src.width, src.height, *src.bounds)
        new_meta.update({
            'crs': dst_crs,
            'transform': transform,
            'width': width,
            'height': height,
            'nodata': -28762
        })
        with rasterio.open(
                warped_tif_path, 'w', compress='LZW', tiled=True,
                **new_meta) as dst:
            for i in range(1, src.count):
                reproject(
                    source=rasterio.band(src, i),
                    destination=rasterio.band(dst, i),
                    src_transform=src.transform,
                    src_crs=src.crs,
                    dst_transform=transform,
                    dst_crs=dst_crs,
                    resampling=Resampling.nearest,
                    src_nodata=-28762
                )


def hdf_to_geotiffs(modis_path, local_dir):
    # Separate out into tifs for each band
    logger.info('Splitting MODIS bands from SDS %s', modis_path)
    split_base_path = os.path.join(local_dir, 'split.tif')
    translate_command = [
        'gdal_translate', '-sds', modis_path, split_base_path
    ]
    subprocess.check_call(translate_command)
    return glob.glob('{}/split*'.format(local_dir))


def create_geotiffs(modis_path, local_dir):
    """Create geotiffs from MODIS HDF

    1. Create separate tifs for each band
    2. Combine tifs into a single tif
    3. Warp tif to web mercator (for quicker access)
    4. Generate COG

    Args:
        modis_path (str): path to modis HDF file
        local_dir (str): directory to output tiffs to
    """
    logger.info('Preparing to create geotiffs')

    post_web_mercator_path = os.path.join(local_dir, 'warp2.tif')

    tifs = sorted(hdf_to_geotiffs(modis_path, local_dir))
    logger.info('Tifs: %s', '\n'.join(tifs))
    warped_paths = cog.warp_tifs(tifs, local_dir)
    merged_tif = cog.merge_tifs(warped_paths, local_dir)
    warp_tif(merged_tif, post_web_mercator_path)
    cog.add_overviews(post_web_mercator_path)
    cog_path = cog.convert_to_cog(post_web_mercator_path, local_dir)
    return [cog_path]
