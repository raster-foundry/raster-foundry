"""Creates a data/tile footprint"""
import json
import logging
import subprocess
import tempfile

import rasterio

from rf.models import Footprint
from rf.uploads.landsat8.io import get_tempdir

logger = logging.getLogger(__name__)


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
        _, warped_path = tempfile.mkstemp(suffix='.TIF', dir=temp_dir)
        _, geojson_path = tempfile.mkstemp(suffix='.GEOJSON', dir=temp_dir)

        with rasterio.open(tif_path) as src:
            y, x = src.shape
            aspect = y / float(x)
            x_size = 512
            y_size = int(512 * aspect)

        resample_cmd = ['gdal_translate', tif_path, resampled_tif_path, '-outsize', str(x_size), str(y_size)]
        warp_cmd = ['gdalwarp', '-co', 'compress=LZW', '-dstnodata', '0', '-dstalpha',
                    '-t_srs', 'epsg:4326', resampled_tif_path, warped_path]
        polygonize_cmd = ['gdal_polygonize.py', '-b', 'mask', warped_path, '-f',
                          'GEOJSON', geojson_path]

        subprocess.check_call(resample_cmd)
        subprocess.check_call(warp_cmd)
        subprocess.check_call(polygonize_cmd)
        with open(geojson_path, 'r+') as fh:
            geojson = json.load(fh)

        data_footprint = [feature['geometry']['coordinates'] for feature in geojson['features'] if feature['properties']['DN'] == 255]

        xs = []
        ys = []

        for area in data_footprint:
          xst, yst = zip(*area[0])
          xs += xst
          ys += yst

        xmin = min(xs)
        xmax = max(xs)
        ymin = min(ys)
        ymax = max(ys)

        tile_footprint = [[[
            [xmin, ymin],
            [xmax, ymin],
            [xmax, ymax],
            [xmin, ymax],
            [xmin, ymin]
        ]]]

        return (Footprint(organization_id, tile_footprint),
                Footprint(organization_id, data_footprint))
