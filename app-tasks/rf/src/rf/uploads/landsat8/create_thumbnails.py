"""Creates thumbnails for landsat 8 imagery

At this point raster foundry reuses the preview thumbnail
in the tile directory
"""

from .io import get_landsat_url
from .settings import organization

from rf.models import Thumbnail


def create_thumbnails(scene_id, landsat_id):
    """Creates thumbnails based on scene_id

    Args:
        scene_id (str): id of the scene (e.g. LC81351172016273LGN00)

    Returns:
        List[Thumbnail]
    """

    path = get_landsat_url(landsat_id)

    small_url = path + '_'.join([landsat_id, 'thumb', 'small']) + '.jpg'
    large_url = path + '_'.join([landsat_id, 'thumb', 'large']) + '.jpg'

    return [
        Thumbnail(organization, 228, 233, 'SMALL', small_url, sceneId=scene_id),
        Thumbnail(organization, 1143, 1168, 'LARGE', large_url, sceneId=scene_id)
    ]
