"""Creates scenes for landsat 8 imagery given a csv row"""


import logging
import uuid

from rf.models import Scene
from rf.utils.io import (
    IngestStatus,
    JobStatus,
    Visibility,
    s3_obj_exists
)

from .create_bands import create_bands
from .create_images import create_images
from .create_thumbnails import create_thumbnails
from .create_footprint import create_footprints
from .settings import organization, aws_landsat_base, datasource_id
from .io import get_landsat_path


logger = logging.getLogger(__name__)


def filter_empty_keys(kv_dict, ok_values=[0, False]):
    """Returns the dictionary that is the subset of kv_dict with empty values removed.
    This method will not recurse into the dictionary to remove empty values from any
    nested dictionaries, so performance is only guaranteed for one-level deep dicts.

    Args:
        kv_dict (dict): one level deep dictionary
        ok_values (list): list of false-y values to consider non-empty (default: [0, False])

    Returns:
        dict
    """

    return {k: v for k, v in kv_dict.iteritems() if v or v in ok_values}


def create_landsat8_scenes(csv_row):
    """Returns scenes that can be created via API given a path to tiles for Landsat 8

    Args:
        csv_row (dict): value returned by a call to DictReader.next on the tiles csv

    Returns:
        List[Scene]
    """

    scene_id = str(uuid.uuid4())
    landsat_id = csv_row.pop('sceneID')
    tileFootprint, dataFootprint = create_footprints(csv_row)
    landsat_path = get_landsat_path(landsat_id)
    if not s3_obj_exists(aws_landsat_base + landsat_path + 'index.html'):
        logger.warn(
            'AWS and USGS are not always in sync. Try again in several hours.\n'
            'If you believe this message is in error, check %s manually.',
            aws_landsat_base + landsat_path
        )
        return []
    timestamp = csv_row.pop('acquisitionDate') + 'T00:00:00.000Z'
    cloud_cover = float(csv_row.pop('cloudCoverFull'))
    sun_elevation = float(csv_row.pop('sunElevation'))
    sun_azimuth = float(csv_row.pop('sunAzimuth'))
    bands_15m = create_bands('15m')
    bands_30m = create_bands('30m')

    tags = ['Landsat 8', 'GeoTIFF']

    scene_metadata = filter_empty_keys(csv_row)

    # Landsat 8 provides a panchromatic band at 15m resolution and all
    # other bands at 30m resolution

    scene = Scene(
        organization,
        0,
        Visibility.PUBLIC,
        tags,
        datasource_id,
        scene_metadata,
        'L8 {}'.format(landsat_path),  # name
        JobStatus.SUCCESS,
        JobStatus.SUCCESS,
        IngestStatus.NOTINGESTED,
        id=scene_id,
        acquisitionDate=timestamp,
        cloudCover=cloud_cover,
        sunAzimuth=sun_azimuth,
        sunElevation=sun_elevation,
        tileFootprint=tileFootprint,
        dataFootprint=dataFootprint,
        metadataFiles=[aws_landsat_base + landsat_path + landsat_id + '_MTL.txt'],
        thumbnails=create_thumbnails(scene_id, landsat_id),
        images=(
            create_images(scene_id, landsat_id, 15, bands_15m) +
            create_images(scene_id, landsat_id, 30, bands_30m)
        )
    )

    return [scene]
