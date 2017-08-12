import os
import uuid
import tempfile

import boto3
import requests

from rf.models import Scene, Footprint, Thumbnail
from rf.utils.io import Visibility, JobStatus, IngestStatus, delete_file

from .create_footprints import bbox_from_planet_feature
from ..geotiff.create_images import create_geotiff_image

import logging
logger = logging.getLogger(__name__)


def get_planet_thumbnail(organization_id, thumbnail_uri, planet_key, scene_id):
    """Download planet thumbnail, push to S3, create RF thumbnail

    Args:
        organization_id (str): organization requiring thumbnail
        thumbnail_uri (str): source thumbnail
        planet_key (str): planet API key for authentication
        scene_id (str): scene to attach thumbnail to

    Returns:
        Thumbnail

    """
    _, local_filepath = tempfile.mkstemp()
    params = {'api_key': planet_key}

    logger.info('Downloading thumbnail for Planet scene: %s', thumbnail_uri)
    response = requests.get(thumbnail_uri, params=params, stream=True)

    with open(local_filepath, 'wb') as filehandle:
        for chunk in response.iter_content(1024):
            filehandle.write(chunk)

    thumbnail_key = '{}.png'.format(scene_id)
    thumbnail_uri = '/thumbnails/{}.png'.format(scene_id)
    logger.info('Uploading thumbnails to S3: %s', thumbnail_key)
    s3_bucket_name = os.getenv('THUMBNAIL_BUCKET')
    s3_bucket = boto3.resource('s3').Bucket(s3_bucket_name)
    s3_bucket.upload_file(local_filepath, thumbnail_key, {'ContentType': 'image/png'})
    delete_file(local_filepath)

    return Thumbnail(
        organization_id,
        256,
        256,
        'SMALL',
        thumbnail_uri,
        sceneId=scene_id
    )


def create_planet_scene(planet_feature, datasource, organization_id, planet_key,
                        ingest_status=IngestStatus.TOBEINGESTED,
                        visibility=Visibility.PRIVATE, tags=[], owner=None):
    """Create a Raster Foundry scene from Planet scenes

    Args:
        planet_key (str): API auth key for planet API
        planet_feature (dict): json response from planet API client
        datasource (str): UUID of the datasource this scene belongs to
        organization_id (str): UUID of the organization that owns the scene
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
        ingest_status,
        [],
        owner=owner,
        images=images,
        **scene_kwargs
    )

    thumbnail_url = planet_feature['_links']['thumbnail']
    scene.thumbnails = [get_planet_thumbnail(organization_id, thumbnail_url, planet_key, scene.id)]

    return scene
