import logging
import os

import click
from planet import api

from ..models import Upload
from ..uploads.geotiff import GeoTiffS3SceneFactory
from ..uploads.landsat_historical import LandsatHistoricalSceneFactory
from ..uploads.planet.factories import PlanetSceneFactory
from ..uploads.modis.factories import MODISSceneFactory
from ..utils.exception_reporting import wrap_rollbar
from ..utils.io import get_session

logger = logging.getLogger(__name__)
HOST = os.getenv('RF_HOST')


@click.command(name='process-upload')
@click.argument('upload_id')
@wrap_rollbar
def process_upload(upload_id):
    """Create Raster Foundry scenes and attach to relevant projects

    Args:
        upload_id (str): ID of Raster Foundry upload to process
    """
    click.echo("Processing upload")

    logger.info('Getting upload')
    upload = Upload.from_id(upload_id)
    logger.info('Updating upload status')
    upload.update_upload_status('Processing')

    logger.info('Processing upload (%s) for user %s with files %s',
                upload.id, upload.owner, upload.files)

    try:
        if upload.uploadType.lower() in ['local', 's3']:
            logger.info('Processing a geotiff upload')
            factory = GeoTiffS3SceneFactory(upload)
        elif upload.uploadType.lower() == 'planet':
            logger.info('Processing a planet upload. This might take a while...')
            logger.info('Retrieving Planet API Client')
            client = api.ClientV1(upload.metadata.get('planetKey'))
            factory = PlanetSceneFactory(
                upload.files,
                upload.datasource,
                upload.id,
                client,
                upload.projectId,
                upload.visibility,
                [],
                upload.owner
            )
        elif upload.uploadType.lower() == 'modis_usgs':
            logger.info('Processing MODIS upload from USGS')
            factory = MODISSceneFactory(
                upload.files,
                upload.datasource,
                upload.id,
                upload.projectId,
                upload.visibility,
                upload.owner
            )
        elif upload.uploadType.lower() in ['landsat_historical']:
            logger.info('Processing historical Landsat data from USGS and GCS')
            factory = LandsatHistoricalSceneFactory(upload)
        else:
            raise Exception('upload type ({}) didn\'t make any sense'.format(upload.uploadType))
        scenes = factory.generate_scenes()
        logger.info('Creating scene objects for upload %s, preparing to POST to API', upload.id)

        created_scenes = [scene.create() for scene in scenes]
        logger.info('Successfully created %s scenes (%s)', len(created_scenes), [s.id for s in created_scenes])

        if upload.projectId:
            logger.info('Upload specified a project. Linking scenes to project %s', upload.projectId)
            scene_ids = [scene.id for scene in created_scenes]
            batch_scene_to_project_url = '{HOST}/api/projects/{PROJECT}/scenes'.format(HOST=HOST, PROJECT=upload.projectId)
            session = get_session()
            response = session.post(batch_scene_to_project_url, json=scene_ids)
            response.raise_for_status()
        upload.update_upload_status('Complete')
        logger.info('Finished importing scenes for upload (%s) for user %s with files %s',
                    upload.id, upload.owner, upload.files)
    except:
        logger.error('Failed to process upload (%s) for user %s with files %s',
                     upload.id, upload.owner, upload.files)
        upload.update_upload_status('FAILED')
        raise
