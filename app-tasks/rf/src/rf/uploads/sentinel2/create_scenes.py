"""Creates scenes for sentinel 2 imagery given a tile path"""

import json
import logging

from rf.models import Scene
from rf.utils.io import JobStatus, Visibility

from .settings import bucket, s3, organization
from .create_footprint import create_footprint
from .create_thumbnails import create_thumbnails
from .create_images import create_images

logger = logging.getLogger(__name__)


def get_tileinfo(path):
    """Gets dictionary representation for scene tileinfo given a path

    Args:
        path (str): prefix for scene tile info (e.g. tiles/54/M/XB/2016/9/25/0)

    Returns:
        dict
    """
    tileinfo_path = '{path}/tileInfo.json'.format(path=path)
    logger.info('Getting tileinfo: %s', tileinfo_path)
    return json.loads(s3.Object(bucket.name, tileinfo_path).get()['Body'].read())


def create_sentinel2_scenes(tile_path):
    """Returns scenes that can be created via API given a path to tiles

    Args:
        tile_path (str): path to tile directory (e.g. tiles/54/M/XB/2016/9/25/0)

    Returns:
        List[Scene]
    """
    logger.info('Starting scene creation for sentinel 2 scene: %s', tile_path)
    tileinfo = get_tileinfo(tile_path)
    footprint = create_footprint(tileinfo)
    thumbnails = create_thumbnails(tile_path)
    tags = ['Sentinel-2', 'JPEG2000']
    datasource = 'Sentinel-2'

    scene_metadata = dict(
        path=tileinfo['path'],
        timestamp=tileinfo['timestamp'],
        utmZone=tileinfo['utmZone'],
        latitudeBand=tileinfo['latitudeBand'],
        gridSquare=tileinfo['gridSquare'],
        dataCoveragePercentage=tileinfo['dataCoveragePercentage'],
        cloudyPixelPercentage=tileinfo['cloudyPixelPercentage'],
        productName=tileinfo['productName'],
        productPath=tileinfo['productPath']
    )

    scene_10m = Scene(
        organization,
        0,
        Visibility.PUBLIC,
        10,
        tags,
        datasource,
        scene_metadata,
        'S2 {} {}'.format(tile_path, '10m'), # name
        JobStatus.SUCCESS if thumbnails else JobStatus.FAILURE,
        JobStatus.SUCCESS if footprint else JobStatus.FAILURE,
        JobStatus.QUEUED,
        acquisitionDate=tileinfo['timestamp'],
        cloudCover=tileinfo['cloudyPixelPercentage'],
        footprint=footprint,
        thumbnails=thumbnails,
        images=create_images(tileinfo, 10)
    )

    scene_20m = Scene(
        organization,
        0,
        Visibility.PUBLIC,
        20,
        tags,
        datasource,
        scene_metadata,
        'S2 {} {}'.format(tile_path, '20m'), # name
        JobStatus.SUCCESS if thumbnails else JobStatus.FAILURE,
        JobStatus.SUCCESS if footprint else JobStatus.FAILURE,
        JobStatus.QUEUED,
        acquisitionDate=tileinfo['timestamp'],
        cloudCover=tileinfo['cloudyPixelPercentage'],
        footprint=footprint,
        thumbnails=thumbnails,
        images=create_images(tileinfo, 20)
    )

    scene_60m = Scene(
        organization,
        0,
        Visibility.PUBLIC,
        60,
        tags,
        datasource,
        scene_metadata,
        'S2 {} {}'.format(tile_path, '60m'), # name
        JobStatus.SUCCESS if thumbnails else JobStatus.FAILURE,
        JobStatus.SUCCESS if footprint else JobStatus.FAILURE,
        JobStatus.QUEUED,
        acquisitionDate=tileinfo['timestamp'],
        cloudCover=tileinfo['cloudyPixelPercentage'],
        footprint=footprint,
        thumbnails=thumbnails,
        images=create_images(tileinfo, 60)
    )

    return [scene_10m, scene_20m, scene_60m]
