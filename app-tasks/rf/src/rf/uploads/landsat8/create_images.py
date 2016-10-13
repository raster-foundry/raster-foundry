"""Creates images based on tileinfo and resolution requested"""

import logging

from rf.utils.io import Visibility, s3
from rf.models import Image

from .io import get_landsat_url, get_landsat_path
from .settings import organization, resolution_band_lookup, bucket

logger = logging.getLogger(__name__)


def create_images(scene_id, resolution):
    """Creates images based on scene_id

    Args:
        scene_id (str) id of the scene (e.g. LC81351172016273LGN00)

    Returns:
        List[Image]
    """

    s3_dir_path = get_landsat_url(scene_id)

    bands = resolution_band_lookup[resolution]

    tif_paths = ['{scene}_B{band}.TIF'.format(scene=scene_id, band=band) for band in bands]

    def size_from_path(tif_path, scene_id=scene_id):
        """Get the size of a tif in s3"""
        s3_obj = s3.Object(bucket.name, get_landsat_path(scene_id) + tif_path)
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
            scene_id
        ) for tif_path, band in zip(tif_paths, bands)
    ]
