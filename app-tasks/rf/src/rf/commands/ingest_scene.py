import logging
import subprocess
import sys
import copy

import click

from ..models import Scene
from ..ingest import io
from ..utils.exception_reporting import wrap_rollbar

logger = logging.getLogger(__name__)


def execute(cmd):
    popen = subprocess.Popen(cmd, stdout=subprocess.PIPE, universal_newlines=True)
    for stdout_line in iter(popen.stdout.readline, ""):
        yield stdout_line
    popen.stdout.close()
    return_code = popen.wait()
    if return_code:
        raise subprocess.CalledProcessError(return_code, cmd)


@click.command(name="ingest-scene")
@click.argument("scene_id")
@wrap_rollbar
def ingest_scene(scene_id):
    """Ingest a scene into Raster Foundry

    Args:
        scene_id (str): ID of scene to ingest
    """
    ingest(scene_id)


def ingest(scene_id):
    """Separated into another function because the Click annotation messes with calling it from other tasks"""
    originalScene = None
    try:
        logger.info("Converting scene to COG: %s", scene_id)
        try:
            scene = Scene.from_id(scene_id)
            originalScene = copy.deepcopy(scene)
            # updates status
            if scene.ingestStatus != "INGESTED":
                scene.ingestStatus = "INGESTING"
                scene.update()
        except Exception:
            logger.error("Error while setting scene up for ingestion")
            raise
        else:
            logger.info("Fetched and set ingest status on scene to INGESTING")

        image_locations = [
            (x.sourceUri, x.filename)
            for x in sorted(
                scene.images, key=lambda x: io.sort_key(scene.datasource, x.bands[0])
            )
        ]

        try:
            io.create_cog(image_locations, scene).update()
        except Exception:
            logger.error("There as an error converting the scene to a COG")
            raise

        logger.info("Cog created, writing histogram to attribute store")
        try:
            metadata_to_postgres(scene.id)
        except Exception:
            logger.error("error while writing metadata to postgres")
            raise

        logger.info("Histogram added to attribute store, updating scene ingest status")
        try:
            scene.ingestStatus = "INGESTED"
            scene.update()
        except Exception as e:
            logger.error("Error updating scene status after ingestion:\n%s", e)
            raise

        logger.info("Scene finished ingesting. Notifying interested parties")
        try:
            notify_for_scene_ingest_status(scene.id)
        except subprocess.CalledProcessError as e:
            logger.error(
                "There was error while notifying users of ingest status, but the ingest succeeded:\n%s",
                e,
            )

    except Exception as e:
        logger.error("There was an unrecoverable error during the ingest:\n%s", e)
        if originalScene is not None:
            try:
                logger.info("Reverting scene and setting status to failed.")
                originalScene.ingestStatus = "FAILED"
                originalScene.update()
                logger.info("Scene status set to failed.")
            except Exception as e:
                logger.error(
                    "Unable to revert scene to original state with failed status:\n%s",
                    e,
                )
        sys.exit("There was an unrecoverable error during scene ingestion: %s" % e)


def metadata_to_postgres(scene_id):
    """Save histogram for the generated COG in the database

    Args:
        scene_id (str): ID of scene to save metadata for
    """

    bash_cmd = [
        "java",
        "-cp",
        "/opt/raster-foundry/jars/batch-assembly.jar",
        "com.rasterfoundry.batch.Main",
        "cog-histogram-backfill",
        scene_id,
    ]

    logger.debug("Bash command to store histogram: %s", " ".join(bash_cmd))
    for output in execute(bash_cmd):
        logger.info(output)

    logger.info("Successfully completed metadata postgres write for scene %s", scene_id)
    return True


def notify_for_scene_ingest_status(scene_id):
    """Notify users that are using this scene as well as the scene owner that
    the ingest status of this scene has changed

    Args:
        scene_id (Scene): the scene which has an updated status
    """

    bash_cmd = [
        "java",
        "-cp",
        "/opt/raster-foundry/jars/batch-assembly.jar",
        "com.rasterfoundry.batch.Main",
        "notify_ingest_status",
        scene_id,
    ]

    for output in execute(bash_cmd):
        logger.info(output)
    return True
