import uuid

from rf.models import Scene
from rf.utils.io import Visibility, JobStatus

from ..geotiff.create_images import create_geotiff_image

import logging
logger = logging.getLogger(__name__)


def create_planet_scene(planet_feature, datasource, planet_key,
                        visibility=Visibility.PRIVATE, tags=[], owner=None):
    """Create a Raster Foundry scene from Planet scenes

    Args:
        planet_key (str): API auth key for planet API
        planet_feature (dict): json response from planet API client
        datasource (str): UUID of the datasource this scene belongs to
        visibility (Visibility): visibility for created scene
        tags (str[]): list of tags to attach to the created scene
        owner (str): user ID of the user who owns the scene

    Returns:
        Scene
    """

    props = planet_feature['properties']
    datasource = datasource
    name = planet_feature['id']
    acquisitionDate = props['acquired']
    cloudCover = props['cloud_cover']
    visibility = visibility
    tags = tags

    scene_kwargs = {
        'sunAzimuth': props['sun_azimuth'],
        'sunElevation': props['sun_elevation'],
        'cloudCover': cloudCover,
        'acquisitionDate': acquisitionDate,
        'id': str(uuid.uuid4()),
        'thumbnails': None,
        'ingestLocation': planet_feature['added_props']['s3Location'].replace(
            '|', '%7C'
        )
    }

    images = [create_geotiff_image(
        planet_feature['added_props']['localPath'],
        planet_feature['added_props']['s3Location'],
        scene=scene_kwargs['id'],
        visibility=visibility,
        owner=owner
    )]

    scene = Scene(
        visibility,
        tags,
        datasource,
        props,
        name,
        JobStatus.QUEUED,
        JobStatus.QUEUED,
        'INGESTED',
        [],
        owner=owner,
        images=images,
        sceneType='COG',
        **scene_kwargs
    )

    return scene
