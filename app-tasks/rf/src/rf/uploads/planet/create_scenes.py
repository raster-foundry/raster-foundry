import json
import os
import uuid


from botocore.errorfactory import ClientError
import boto3


from rf.models import Scene, Footprint
from rf.utils.io import Visibility, JobStatus, IngestStatus
from .create_footprints import bbox_from_planet_feature
from ..geotiff.create_images import create_geotiff_image
from ..geotiff.io import get_geotiff_size_bytes

import logging
logger = logging.getLogger(__name__)


def create_planet_scene(planet_feature, datasource, organization_id,
                        visibility=Visibility.PRIVATE, tags=[], owner=None):
    """Create a Raster Foundry scene from Planet scenes

    Args:
        planet_feature (dict): json response from planet API client
        datasource (str): UUID of the datasource this scene belongs to
        organization_id (str): UUID of the organization that owns the scene
        visibility (Visibility): visibility for created scene
        tags (str[]): list of tags to attach to the created scene
        owner (str): user ID of the user who owns the scene
        file_loc (str): location of the downloaded tif

    Returns:
        Scene
    """

    props = planet_feature['properties']
    datasource = datasource
    name = planet_feature['id']
    acquisitionDate = props['acquired']
    cloudCover = props['cloud_cover']
    ingestSizeBytes = 0 # TODO
    visibility = visibility
    tags = tags
    dataFootprint = planet_feature['geometry']['coordinates']

    scene_kwargs = {
        'sunAzimuth': props['sun_azimuth'],
        'sunElevation': props['sun_elevation'],
        'cloudCover': cloudCover,
        'acquisitionDate': acquisitionDate,
        'id': str(uuid.uuid4()),
        'thumbnails': None,
        'tileFootprint': Footprint(
            organization_id, bbox_from_planet_feature(planet_feature)
        ),
        'dataFootprint': Footprint(
            organization_id,
            [planet_feature['geometry']['coordinates']]
        )
    }

    images = [create_geotiff_image(
        organization_id,
        planet_feature['added_props']['localPath'],
        planet_feature['added_props']['s3Location'],
        scene=scene_kwargs['id'],
        visibility=visibility,
        owner=owner
    )]

    scene = Scene(
        organization_id,
        ingestSizeBytes,
        visibility,
        tags,
        datasource,
        props,
        name,
        JobStatus.QUEUED,
        JobStatus.QUEUED,
        IngestStatus.TOBEINGESTED,
        [],
        owner=owner,
        images=images,
        **scene_kwargs
    )

    return scene
