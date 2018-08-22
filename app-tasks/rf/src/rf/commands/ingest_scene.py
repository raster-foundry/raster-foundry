import logging
import subprocess

import click

from ..models import Scene
from ..ingest import io
from ..utils.exception_reporting import wrap_rollbar

logger = logging.getLogger(__name__)

@click.command(name='ingest-scene')
@click.argument('scene_id')
@wrap_rollbar
def ingest_scene(scene_id):
    """Ingest a scene into Raster Foundry

    Args:
        scene_id (str): ID of scene to ingest
    """
    logger.info("Converting scene to COG: %s", scene_id)
    scene = Scene.from_id(scene_id)
    scene.ingestStatus = 'INGESTING'
    scene.update()
    image_locations = [
        (x.sourceUri, x.filename) for x in
        sorted(scene.images, key=lambda x: x.bands[0].name.split(' - ')[1])
    ]
    io.create_cog(image_locations, scene)


def metadata_to_postgres(uri, scene_id):
    """Save histogram for the generated COG in the database

    Args:
        uri (str): remote location of layer
        scene_id (str): ID of scene to save metadata for
    """

    # TODO: this will need to change once there's a command for generating the histogram
    # for a COG scene
    bash_cmd = [
        'java', '-cp',
        '/opt/raster-foundry/jars/rf-batch.jar',
        'com.azavea.rf.batch.Main',
        'SOME NEW JOB NAME',
        uri,
        'layer_attributes',
        scene_id
    ]

    logger.debug('Bash command to store histogram: %s', ' '.join(bash_cmd))
    running_cmd = subprocess.Popen(bash_cmd)
    running_cmd.communicate()
    logger.info('Successfully completed metadata postgres write for scene %s', scene_id)
    return True


def notify_for_scene_ingest_status(scene_id):
    """Notify users that are using this scene as well as the scene owner that
    the ingest status of this scene has changed

    Args:
        scene_id (Scene): the scene which has an updated status
    """

    bash_cmd = [
        'java', '-cp',
        '/opt/raster-foundry/jars/rf-batch.jar',
        'com.azavea.rf.batch.Main',
        'notify_ingest_status',
        scene_id
    ]
    running_process = subprocess.Popen(bash_cmd)
    running_process.communicate()
    return True
