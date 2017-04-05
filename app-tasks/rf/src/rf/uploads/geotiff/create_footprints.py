"""Creates a data/tile footprint"""
import logging
import tempfile
import numpy as np
from pyproj import Proj, transform
import rasterio
from rasterio.features import shapes

from rf.models import Footprint
from rf.uploads.landsat8.io import get_tempdir

logger = logging.getLogger(__name__)


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
    with rasterio.open(local_tif_path) as src:
        kwargs = src.meta.copy()
        kwargs.update({
            'count': 1,
            'nodata': 0,
            'dtype': rasterio.ubyte,
            'compress': 'lzw'
        })

        with rasterio.open(data_mask_tif_path, 'w', **kwargs) as dst:
            for _, window in src.block_windows(1):
                block = src.read(4, window=window)
                block[block < 255] = 1
                block[block == 255] = 0
                dst.write(block.astype(rasterio.ubyte), window=window, indexes=1)

        with rasterio.open(tile_mask_tif_path, 'w', **kwargs) as dst:
            for _, window in src.block_windows(1):
                block = src.read(4, window=window)
                block[block <= 255] = 1
                dst.write(block.astype(rasterio.ubyte), window=window, indexes=1)

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
    copied_feature = feature.copy()
    coords_array = copied_feature['coordinates']
    for index, coords in enumerate(copied_feature['coordinates']):
        reproj_coords = coord_transform(coords, src_crs, target_crs)
        coords_array[index] = reproj_coords
    feature['coordinates'] = [coords_array]
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
    geoms = shapes(raster, mask=mask, transform=src_affine, connectivity=8)

    footprint, value = geoms.next()

    assert value == 1.0, 'Geometry should be of value 1, got %r' % value

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
        tile_mask_tif_path, data_mask_tif_path = create_tif_mask(temp_dir, tif_path)
        data_footprint = extract_polygon(data_mask_tif_path)
        tile_footprint = extract_polygon(tile_mask_tif_path)
        return (Footprint(organization_id, tile_footprint),
                Footprint(organization_id, data_footprint))
