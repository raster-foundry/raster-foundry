import logging
import os

import click
from planet import api

from ..models import Upload, AnnotationProject
from ..uploads.geotiff import GeoTiffS3SceneFactory
from ..uploads.geotiff.io import update_annotation_project
from ..utils.exception_reporting import wrap_rollbar
from ..utils.io import get_session, notify_intercom, copy_to_debug

logger = logging.getLogger(__name__)
HOST = os.getenv("RF_HOST")
JOB_ATTEMPT = int(os.getenv("AWS_BATCH_JOB_ATTEMPT", -1))


class TaskGridError(Exception):
    pass


@click.command(name="process-upload")
@click.argument("upload_id")
@wrap_rollbar
def process_upload(upload_id):
    """Create Raster Foundry scenes and attach to relevant projects

    Args:
        upload_id (str): ID of Raster Foundry upload to process
    """
    click.echo("Processing upload")

    logger.info("Getting upload")
    upload = Upload.from_id(upload_id)
    logger.info("Updating upload status")
    upload.update_upload_status("Processing")
    annotationProject = None
    if upload.annotationProjectId is not None:
        logger.info("Getting annotation project: %s", upload.annotationProjectId)
        annotationProject = AnnotationProject.from_id(upload.annotationProjectId)
        logger.info("Updating annotation project status")
        annotationProject.update_status({"progressStage": "PROCESSING"})

    logger.info(
        "Processing upload (%s) for user %s with files %s",
        upload.id,
        upload.owner,
        upload.files,
    )

    try:
        if upload.uploadType.lower() in ["local", "s3"]:
            logger.info("Processing a geotiff upload")
            factory = GeoTiffS3SceneFactory(upload)
        else:
            raise Exception(
                """upload type ({}) didn't make any sense.
                Non-geotiff uploads were removed as of 1.51.0""".format(
                    upload.uploadType
                )
            )
        scenes = factory.generate_scenes()
        logger.info(
            "Creating scene objects for upload %s, preparing to POST to API", upload.id
        )

        created_scenes = [scene.create() for scene in scenes]
        logger.info(
            "Successfully created %s scenes (%s)",
            len(created_scenes),
            [s.id for s in created_scenes],
        )

        if upload.layerId and upload.projectId:
            logger.info(
                "Upload specified to a project layer. Linking scenes to layer %s",
                upload.layerId,
            )
            scene_ids = [scene.id for scene in created_scenes]
            batch_scene_to_layer_url = (
                "{HOST}/api/projects/{PROJECT}/layers/{LAYER}/scenes".format(
                    HOST=HOST, PROJECT=upload.projectId, LAYER=upload.layerId
                )
            )
            session = get_session()
            response = session.post(batch_scene_to_layer_url, json=scene_ids)
            response.raise_for_status()
        elif upload.projectId:
            logger.info(
                "Upload specified a project. Linking scenes to project %s",
                upload.projectId,
            )
            scene_ids = [scene.id for scene in created_scenes]
            batch_scene_to_project_url = "{HOST}/api/projects/{PROJECT}/scenes".format(
                HOST=HOST, PROJECT=upload.projectId
            )
            session = get_session()
            response = session.post(batch_scene_to_project_url, json=scene_ids)
            response.raise_for_status()
        upload.update_upload_status("Complete")
        logger.info(
            "Finished importing scenes for upload (%s) for user %s with files %s",
            upload.id,
            upload.owner,
            upload.files,
        )

        generate_tasks = upload.annotationProjectId is not None and upload.generateTasks
        if generate_tasks:
            try:
                [
                    update_annotation_project(
                        upload.annotationProjectId,
                        scene.ingestLocation.replace("%7C", "|"),
                    )
                    for scene in created_scenes
                ]
            except Exception as e:
                raise TaskGridError("Error making task grid: %s", e)
    except TaskGridError as tge:
        logger.error(
            "Error making task grids annotation project (%s) on upload (%s) for with files %s. %s",
            annotationProject.id,
            upload.id,
            upload.files,
            tge,
        )
        if JOB_ATTEMPT >= 3:
            upload.update_upload_status("FAILED")
            annotationProject.update_status({"errorStage": "TASK_GRID_FAILURE"})
        else:
            upload.update_upload_status("QUEUED")
            if annotationProject is not None:
                annotationProject.update_status({"progressStage": "QUEUED"})
        raise
    except:
        if JOB_ATTEMPT >= 3 and annotationProject is not None:
            logger.error(
                "Upload for AnnotationProject failed to process: %s",
                annotationProject.id,
            )
            upload.update_upload_status("FAILED")
            annotationProject.update_status({"errorStage": "IMAGE_INGESTION_FAILURE"})
            notify_intercom(
                upload.owner,
                (
                    'Your project "{annotationProjectName}" failed to process. If '
                    "you'd like help troubleshooting, please reach out to us here or at "
                    "groundwork@azavea.com."
                ).format(annotationProjectName=annotationProject.name),
            )
            copy_to_debug(upload)
        elif JOB_ATTEMPT >= 3:
            upload.update_upload_status("FAILED")
            copy_to_debug(upload)
        else:
            upload.update_upload_status("QUEUED")
            if annotationProject is not None:
                annotationProject.update_status({"progressStage": "QUEUED"})
        logger.error(
            "Failed to process upload (%s) for user %s with files %s",
            upload.id,
            upload.owner,
            upload.files,
        )
        raise
