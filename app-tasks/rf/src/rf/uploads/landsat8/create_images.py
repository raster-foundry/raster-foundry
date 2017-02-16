"""Creates images based on tileinfo and resolution requested"""

import logging

from rf.utils.io import Visibility, s3
from rf.models import Image

from .io import get_landsat_url, get_landsat_path
from .settings import organization, bucket

logger = logging.getLogger(__name__)


def create_images(scene_id, landsat_id, resolution, bands):
    """Creates images based on scene_id. Created image is a python representation
    of the Image.Create case class in the scala datamodel

    Args:
        scene_id (str) id of the scene (e.g. LC81351172016273LGN00)

    Returns:
        List[Image]
    """

    s3_dir_path = get_landsat_url(landsat_id)

    def usgs_band_no(band):
        """Helper to get USGS's band number from a band object"""

        return band.name.split(' - ')[1]

    tif_paths_with_bands = [
        ('{scene}_B{band}.TIF'.format(scene=landsat_id, band=usgs_band_no(band)), band)
        for band in bands
    ]

    def size_from_path(tif_path, landsat_id=landsat_id):
        """Get the size of a tif in s3"""

        logger.info("Getting object size for path: %s", get_landsat_path(landsat_id) + tif_path)
        s3_obj = s3.Object(bucket.name, get_landsat_path(landsat_id) + tif_path)
        return s3_obj.content_length

    return [
        Image(
            organization,
            size_from_path(tif_path),
            Visibility.PUBLIC,
            tif_path,
            ''.join([s3_dir_path, tif_path]),
            [band],
            {},
            resolution,
            [],
            scene_id
        ) for tif_path, band in tif_paths_with_bands
    ]
