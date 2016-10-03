"""Creates thumbnails for sentinel 2 imagery

At this point raster foundry reuses the preview
thumbnail in the tile directory
"""

import requests

from .settings import base_http_path, organization
from rf.models import Thumbnail


def create_thumbnails(tile_path):
    """Creates thumbnail based on tile_path

    Args:
        tile_path (str): path to tile directory (e.g. tiles/54/M/XB/2016/9/25/0)

    Returns:
        List[Thumbnail]
    """
    key_path = '{tile_path}/preview.jpg'.format(tile_path=tile_path)
    thumbnail_url = base_http_path.format(key_path=key_path)

    response = requests.head(thumbnail_url)
    if response.status_code == 404:
        return []
    else:
        return [Thumbnail(
            organization,
            343, 343,
            'SQUARE',
            thumbnail_url
        )]
