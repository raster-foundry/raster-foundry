import json
import logging
import os

import click
import geopandas as gpd

from ..utils.get_hitl_input import get_input
from ..utils.notify_intercom import notify
from ..utils.persist_hitl_output import persist_hitl_output
from ..utils.post_process import post_process

from hitl.utils.get_hitl_input import get_input
from hitl.utils.notify_intercom import notify
from hitl.utils.persist_hitl_output import persist_hitl_output
from hitl.utils.post_process import post_process

from hitl.rv.active_learning import active_learning_step
from hitl.rv.io import get_class_config

logging.basicConfig(format='%(levelname)s:%(message)s', level=logging.INFO)
logger = logging.getLogger(__name__)

HOST = os.getenv("RF_HOST")
GROUNDWORK_URL_BASE = os.getenv("GROUNDWORK_URL_BASE")
JOB_ATTEMPT = int(os.getenv("AWS_BATCH_JOB_ATTEMPT", -1))
OUTPUT_DIR = os.getenv("HITL_OUTPUT_BUCKET", "/tmp/hitl/out")


@click.command(name="run")
@click.argument("job_id")
def run_hitl(job_id: str):
    """Run a HITL job to generate label predictions

    Args:
        job_id (str): ID of a HITL Job to run
    """
    # STEP 1 Get HITL input from API
    # - Enhancement later: save task and label to a file
    # - labels as a GeoJSON URI
    # - grab previous iteration
    logger.info("Getting HITL project data from GroundWork")
    scene, tasks, labels, label_classes, job, all_prev_jobs = get_input(job_id)

    # STEP 2 Train and predict with RV
    # * Output:
    #   - Labels in GeoJSON
    #   - Task with priority scores
    #   - Enhancement later: save task and label to files

    # RV expects labels to be in a GeoJSON file
    logger.info("Saving human labels to a file...")
    labels_uri = "labels.geojson"
    with open(labels_uri, "w") as f:
        json.dump(labels, f)
    logger.info("Reading tasks into a GeoDataFrame...")
    task_grid_gdf = gpd.GeoDataFrame.from_features(tasks)
    task_grid_gdf["taskId"] = task_grid_gdf["id"]

    # task_grid_with_scores is a GeoDataFrame with a "priorityScore" column
    # pred_geojson_uri is the path (str) to the predictions GeoJSON file
    logger.info("Starting the Raster Vision training and predicting...")
    iter_num = job.version
    output_location = f"{OUTPUT_DIR}/{job_id}/{iter_num}/"
    last_output_dir = None
    if len(all_prev_jobs) != 0:
        last_job = all_prev_jobs[-1]
        version_num = last_job["version"]
        prev_job_id = last_job["id"]
        last_output_dir = f"{OUTPUT_DIR}/{prev_job_id}/{version_num}/"
    task_grid_with_scores, pred_geojson_uri = active_learning_step(
        iter_num=iter_num,
        class_config=get_class_config(label_classes),
        img_info=scene,
        labels_uri=labels_uri,
        task_grid=task_grid_gdf,
        output_dir=output_location,
        last_output_dir=last_output_dir,
        train_kw=dict(
            num_epochs=5, chip_sz=256, img_sz=256, external_model=True),
        predict_kw=dict(chip_sz=256, stride=200, denoise_radius=16))
    logger.info(
        f"Task grid with priority scores... {task_grid_with_scores.to_json()}")
    logger.info(f"Prediction labels location... {pred_geojson_uri}")

    # STEP 3 Process data
    # * Output:
    #    - Clipped labels:
    #       - Add task ID to labels
    #       - Labels clipped to tasks
    #       - Make sure labels in VALIDATED tasks are filtered out
    #    - Task grid:
    #       - Tasks with priority scores
    #       - Tasks with prediction labels are marked as LABELED
    # - Enhancement later: save and read task and label to a file
    logger.info("Processing the task grid and the labels...")
    updated_tasks_dict, labels_to_post_dict = post_process(
        task_grid_with_scores, pred_geojson_uri, label_classes, job_id)

    # STEP 4 Persist data to DB
    # - Update tasks (PUT)
    # - Add prediction labels (POST)
    logger.info("Updating labels and task grid to the API...")
    persist_hitl_output(job.projectId, updated_tasks_dict, labels_to_post_dict)

    # STEP 5 Update batch job status
    logger.info("Updating job status...")
    job.update_job_status("RAN")

    # STEP 6 Notify Intercom
    if HOST is not None:
        project_uri = (
            f"{GROUNDWORK_URL_BASE}/app/campaign/{job.campaignId}/overview?"
            f"s={job.projectId}")
        if GROUNDWORK_URL_BASE is None:
            base = "https://groundwork.azavea.com/app"
            if "staging" in HOST:
                base = (
                    "https://develop--raster-foundry-annotate.netlify.app/app")
            project_uri = (f"{base}/app/campaign/{job.campaignId}/overview?"
                           f"s={job.projectId}")
        logger.info("Notifying user of the HITL prediction labels...")
        message = f"Your HITL prediction labels are ready at: {project_uri}"
        notify(job.owner, message)
