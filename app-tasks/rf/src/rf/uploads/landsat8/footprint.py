"""Functions to extract footprints from a landsat8 file"""

import logging
import tempfile

import numpy as np
from pyproj import Proj, transform
import rasterio
from rasterio.features import shapes

from .io import (
    download_tif,
    get_rf_image,
    get_tempdir,
    upload_footprint
)


logger = logging.getLogger(__name__)


def create_tif_mask(temp_dir, local_tif_path):
    """Runs gdal command to set mask of data/no-data on tif

    Args:
        temp_dir (str): directory to create masked tif in
        local_tif_path (str): local tif to use to create mask
    """
    _, mask_tif_path = tempfile.mkstemp(suffix='.TIF', dir=temp_dir)

    with rasterio.open(local_tif_path) as src:
        kwargs = src.meta.copy()
        kwargs.update({'nodata': 0, 'dtype': rasterio.ubyte, 'compress': 'lzw'})
        with rasterio.open(mask_tif_path, 'w', **kwargs) as dst:
            for _, window in src.block_windows(1):
                block = src.read(1, window=window)
                block[block > 0] = 1
                dst.write(block.astype(rasterio.ubyte), window=window, indexes=1)
    return mask_tif_path


def transform_polygon_coordinates(feature, src_crs, target_crs):
    """Transforms coordinates of a geojson polygon from src to target

    Args:
        feature (dict): geojson polygon
        src_crs (pyproj.Proj): projection of original coordinates
        target_crs (pyproj.Proj): projection to transform coordinates to

    Returns:
        dict
    """
    copied_feature = feature.copy()
    coords = copied_feature['coordinates'][0]
    src_x, src_y = zip(*coords)
    reprojected = transform(src_crs, target_crs, src_x, src_y)
    feature['coordinates'][0] = zip(*reprojected)
    return feature


def extract_polygon(mask_tif_path):
    """Extracts polygon to a geojson dict

    Args:
        mask_tif_path (str): path to tif to extract geojson from

    Returns:
        str: path to geojson file
    """

    with rasterio.open(mask_tif_path, 'r') as src:
        raster = src.read(1)
        src_crs = Proj(init=src.crs.get('init'))
        src_affine = src.affine

    mask = np.ma.masked_equal(raster, 0)
    geoms = shapes(raster, mask=mask, transform=src_affine)
    footprint, value = geoms.next()

    assert value == 1.0, 'Geometry should be of value 1'

    target_crs = Proj(init='epsg:4326')
    feature = transform_polygon_coordinates(footprint, src_crs, target_crs)
    feature_collection = {'type': 'FeatureCollection', 'features': [
        {'type': 'Feature', 'geometry': feature, 'properties': {'value': value}}
    ]}
    return feature_collection


def extract_footprint(rf_image_id):
    """Performs all actions to extract polygon from a landsat scene

    - requests image from raster foundry API
    - extracts scene from image metadata
    - downloads tif to use for polygon mask
    - extracts polygon and converts coords to lat lng
    - uploads geojson to raster foundry api

    TODO: Fix up getting scene from image metadata once endpoint API is defined

    Args:
        scene (str): scene to download and extract polygon
    """
    logger.info('Beginning process to extract footprint for image:%s', rf_image_id)

    rf_image = get_rf_image(rf_image_id)
    scene = rf_image['metadata'].get('scene', None)

    assert scene, 'No scene found in metadata for image {}'.format(rf_image_id)

    with get_tempdir() as temp_dir:
        bands = [1]
        tifs = download_tif(temp_dir, scene, bands)
        mask_tif_path = create_tif_mask(temp_dir, tifs.get(1))
        geojson = extract_polygon(temp_dir, mask_tif_path)
        upload_footprint(rf_image_id, geojson)
