"""Creates a data/tile footprint"""
import logging
import os
import subprocess
import tempfile

import numpy as np
from pyproj import Proj, transform
import rasterio
from rasterio.features import shapes

from rf.models import Footprint
from rf.uploads.landsat8.io import get_tempdir
from rasterio.features import sieve

logger = logging.getLogger(__name__)


FILL_VALUE = 255


def create_tif_mask(temp_dir, local_tif_path):
    """Uses rasterio to create masks for tile and data

    Note: exploits the fact that 255 in the near infrared band indicates
    nodata to create the data footprint mask

    Args:
        temp_dir (str): directory to create masked tif in
        local_tif_path (str): local tif to use to create mask
    """
    _, tile_mask_tif_path = tempfile.mkstemp(suffix='.TIF', dir=temp_dir)
    _, data_mask_tif_path = tempfile.mkstemp(suffix='.TIF', dir=temp_dir)

    logger.info('Creating masks to extract footprints')
    with rasterio.open(local_tif_path) as src:
        kwargs = src.meta.copy()
        kwargs.update({
            'count': 1,
            'nodata': 0,
            'dtype': rasterio.ubyte,
            'compress': 'lzw'
        })

        with rasterio.open(data_mask_tif_path, 'w', **kwargs) as dst:
            mask = sieve(src.dataset_mask(), size=40)
            mask[~np.isnan(mask) & mask != 0] = FILL_VALUE
            dst.write(mask, indexes=1)

        with rasterio.open(tile_mask_tif_path, 'w', **kwargs) as dst:
            for _, window in src.block_windows(1):
                block = src.read(1, window=window)
                block.fill(FILL_VALUE)
                block = block.astype(rasterio.ubyte)
                dst.write(block, window=window, indexes=1)

    return tile_mask_tif_path, data_mask_tif_path


def coord_transform(coords, src_crs, target_crs):
    """Helper function to transform a set of coordinates from src to target

    Args:
        coords (list):
        src_crs (pyproj.Proj): projection of source coordinates
        target_crs (pyproj.Proj): projection to transform to

    Returns:
        list
    """
    src_x, src_y = zip(*coords)
    reprojected = transform(src_crs, target_crs, src_x, src_y)
    return zip(*reprojected)


def transform_polygon_coordinates(feature, src_crs, target_crs):
    """Transforms coordinates of a geojson polygon from src to target

    Args:
        feature (dict): geojson polygon
        src_crs (pyproj.Proj): projection of original coordinates
        target_crs (pyproj.Proj): projection to transform coordinates to

    Returns:
        dict
    """
    logger.info('Transforming footprint coordinates')
    copied_feature = feature.copy()
    coords_array = copied_feature['coordinates']
    for index, coords in enumerate(copied_feature['coordinates']):
        reproj_coords = coord_transform(coords, src_crs, target_crs)
        coords_array[index] = reproj_coords
    feature['coordinates'] = [coords_array]
    return feature


def add_crs_metadata(mask_tif_path):
    """Adds CRS to tifs that lack it

    Args:
        mask_tif_path (str): path to tif to extract geojson from

    Returns:
        str: path to transformed output
    """

    base_path = ''.join(mask_tif_path.split('.')[:-1])
    warped_path = base_path + '-warped.tif'
    warp_cmd = ['gdalwarp', '-t_srs',  'epsg:4326', '-of', 'vrt',
                mask_tif_path, 'warped.vrt']
    translate_cmd = ['gdal_translate', '-co', 'compress=LZW', 'warped.vrt', warped_path]
    try:
        subprocess.check_call(warp_cmd)
        subprocess.check_call(translate_cmd)
    finally:
        os.remove('warped.vrt')
    return warped_path


def extract_polygon(mask_tif_path):
    """Extracts polygon to a geojson dict

    Args:
        mask_tif_path (str): path to tif to extract geojson from

    Returns:
        list: transformed coordinates of the polygon
    """

    with rasterio.open(mask_tif_path, 'r') as src:
        raster = src.read(1)
        start_crs = src.crs.get('init')
        if start_crs is None:
            logger.info('No SRID available in tiff; adding one.')
            new_path = add_crs_metadata(mask_tif_path)
            with rasterio.open(new_path, 'r') as src2:
                src_crs = Proj(init=src2.crs.get('init'))
                src_affine = src2.affine
        else:
            src_crs = Proj(init=src.crs.get('init'))
            src_affine = src.affine

    mask = np.ma.masked_equal(raster, 0)
    logger.info('Extracting shapes from footprint masks')
    geoms = shapes(raster, mask=mask.astype('bool'), transform=src_affine, connectivity=4)
    try:
        footprint, value = geoms.next()
    except StopIteration:
        raise Exception('Shapes could not be extracted from the footprint mask, the file could be invalid')

    assert value == FILL_VALUE, 'Geometry should be of value %s, got %r' % (
        FILL_VALUE, value)

    target_crs = Proj(init='epsg:4326')
    feature = transform_polygon_coordinates(footprint, src_crs, target_crs)
    return feature['coordinates']


def extract_footprints(organization_id, tif_path):
    """Performs all actions to extract polygon from a kayak scene

    Args:
        organization_id (str): organization footprints belong to
        tif_path (str): path to tif to extract polygons from

    Returns:
        tuple
    """
    logger.info('Beginning process to extract footprint for image:%s', tif_path)
    with get_tempdir() as temp_dir:

        _, resampled_tif_path = tempfile.mkstemp(suffix='.TIF', dir=temp_dir)

        with rasterio.open(tif_path) as src:
            y, x = src.shape

            aspect = y / float(x)
        x_size = 512
        y_size = int(512 * aspect)

        # Resample to a max width of 512
        cmd = [
            'gdal_translate', tif_path, resampled_tif_path,
            '-outsize', str(x_size), str(y_size),
        ]
        logger.info('Running GDAL command: %s', ' '.join(cmd))

        subprocess.check_call(cmd)

        tile_mask_tif_path, data_mask_tif_path = create_tif_mask(temp_dir, resampled_tif_path)
        data_footprint = extract_polygon(data_mask_tif_path)
        tile_footprint = extract_polygon(tile_mask_tif_path)

        return (Footprint(organization_id, tile_footprint),
                Footprint(organization_id, data_footprint))
