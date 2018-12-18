import click
import subprocess

from ..models import Scene
from ..ingest import io

from ..utils.exception_reporting import wrap_rollbar
from rf.ingest.settings import (landsat8_datasource_id,
                                sentinel2_datasource_id)

import logging
logger = logging.getLogger(__name__)


@click.command(name='reprocess-geotiff-to-cog')
@click.argument('scene_ids')
@wrap_rollbar
def reprocess_geotiff_to_cog(scene_ids):
    """ Reprocess user uploaded and planet AVRO scenes to COGs

    Should be fine for anything other than landsat / sentinel scenes
    Will not re-ingest cogs.

    Accepts a comma separated list of scene ids
    """

    ids = [scene.strip() for scene in scene_ids.split(',')]
    logger.info("Processing %d scenes", len(ids))

    for scene_id in ids:
        try:
            process_id(scene_id)
        except Exception as e:
            logger.exception("Failed to reprocess geotiff for scene: %s; %s", scene_id, e)


def process_id(scene_id):
    """Process a single scene id"""
    logger.info("Converting AVRO scene to COG: %s", scene_id)
    scene = Scene.from_id(scene_id)
    if scene.ingestStatus != 'INGESTED':
        logger.info("Skipping scene because it is not already ingested")
        return
    if scene.sceneType == 'COG':
        logger.info("Skipping scene because it's already a cog")
        return

    if scene.datasource == sentinel2_datasource_id or scene.datasource == landsat8_datasource_id:
        image_locations = [(x.sourceUri, x.filename) for x in sorted(
            scene.images, key=lambda x: io.sort_key(scene.datasource, x.bands[0]))]
        io.create_cog(image_locations, scene)
    else:
        image_locations = [(x.sourceUri, x.filename) for x in scene.images]
        io.create_cog(image_locations, scene, True)
    logger.info('Cog created, writing histogram to attribute store')
    metadata_to_postgres(scene.id)
    logger.info('Histogram written to attribute store')


def metadata_to_postgres(scene_id):
    """Save histogram for the generated COG in the database

    Args:
        scene_id (str): ID of scene to save metadata for
    """

    bash_cmd = [
        'java', '-cp', '/opt/raster-foundry/jars/batch-assembly.jar',
        'com.rasterfoundry.batch.Main', 'cog-histogram-backfill',
        scene_id
    ]

    logger.debug('Bash command to store histogram: %s', ' '.join(bash_cmd))
    running_cmd = subprocess.Popen(bash_cmd)
    running_cmd.communicate()
    logger.info('Successfully completed metadata postgres write for scene %s',
                scene_id)
    return True
