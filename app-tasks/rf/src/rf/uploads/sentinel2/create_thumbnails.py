"""Creates thumbnails for sentinel 2 imagery

At this point raster foundry reuses the preview
thumbnail in the tile directory
"""

from .settings import base_http_path, organization
from rf.models import Thumbnail
from rf.utils.io import s3_obj_exists


def create_thumbnails(scene_id, tile_path):
    """Creates thumbnail based on tile_path

    Args:
        tile_path (str): path to tile directory (e.g. tiles/54/M/XB/2016/9/25/0)

    Returns:
        List[Thumbnail]
    """
    key_path = '{tile_path}/preview.jpg'.format(tile_path=tile_path)
    thumbnail_url = base_http_path.format(key_path=key_path)

    if not s3_obj_exists(thumbnail_url):
        return []
    else:
        return [Thumbnail(
            organization,
            343, 343,
            'SQUARE',
            thumbnail_url,
            sceneId=scene_id
        )]
