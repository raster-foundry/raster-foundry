import json
from lib2to3.pgen2 import driver
import logging
import os

import click
import geopandas as gpd
from zmq import proxy_steerable

from ..utils.merge_labels import merge_labels_with_task_grid
from ..utils.get_hitl_input import get_input
from ..utils.notify_intercom import notify
from ..utils.persist_hitl_output import persist_hitl_output

from ..rv.active_learning import active_learning_step
from ..rv.io import get_class_config

logging.basicConfig(format='%(levelname)s:%(message)s', level=logging.INFO)
logger = logging.getLogger(__name__)

HOST = os.getenv("RF_HOST")
JOB_ATTEMPT = int(os.getenv("AWS_BATCH_JOB_ATTEMPT", -1))
OUTPUT_DIR = os.getenv("HITL_OUTPUT_BUCKET", "/tmp/hitl/out")


@click.command(name="run")
@click.argument('job_id')
def run_hitl(job_id):
    """Run a HITL job to generate label predictions

    Args:
        job_id (str): ID of a HITL Job to run
    """
    # STEP 1 Get HITL input from API
    # - Enhancement later: save task and label to a file
    # - labels as a GeoJSON URI
    # - grab previous iteration
    logger.info("Grabbing HITL project data from GroundWork")
    scene, tasks, labels, label_classes, job = get_input(job_id)
    project_id = job.projectId

    # STEP 2 Train and predict with RV
    # * Output:
    #   - Labels in GeoJSON
    #   - Task with priority scores
    #   - Enhancement later: save task and label to files

    # RV expects labels to be in a GeoJSON file
    labels_uri = 'labels.geojson'
    with open(labels_uri, 'w') as f:
        json.dump(labels, f)

    task_grid_gdf = gpd.GeoDataFrame.from_features(tasks)

    # task_grid_with_scores is a GeoDataFrame with a "score" column
    # pred_geojson_uri is the path (str) to the predictions GeoJSON file
    iter_num = 0
    output_location = f"{OUTPUT_DIR}/{job_id}/{iter_num}/"

    task_grid_with_scores, pred_geojson_uri = active_learning_step(
        iter_num=iter_num,
        class_config=get_class_config(label_classes),
        img_info=scene,
        labels_uri=labels_uri,
        task_grid=task_grid_gdf,
        output_dir=output_location,
        last_output_dir=None,
        train_kw=dict(
            num_epochs=1, chip_sz=256, img_sz=256, external_model=True),
        predict_kw=dict(chip_sz=256, stride=256, denoise_radius=8))

    logger.info("Task grid with priority scores...")
    logger.info(task_grid_with_scores.to_json())
    logger.info("Prediction labels location...")
    logger.info(pred_geojson_uri)

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
    task_grid_with_scores['status'] = task_grid_with_scores['status'].map(
        lambda x: x.replace('UNLABELED', 'LABELED'))
    task_grid_with_scores.set_index('id', inplace=True)

    # Read RV predictions
    preds = gpd.read_file(pred_geojson_uri)

    merged_preds = merge_labels_with_task_grid(
        preds, task_grid_with_scores)
    machine_labeled_preds = merged_preds[merged_preds['status'] != 'VALIDATED'].copy(
    )

    # merged_preds.to_file(
    #     './hitl/out/pred/pred-merged.geojson', driver='GeoJSON')

    # STEP 4 Persist data to DB
    # TODO: use existing `persist_hitl_output` function
    # - Update tasks (PUT)
    # - Add prediction labels (POST)

    def _transform_geojson_feature(feature):
        p = feature['properties']
        properties = {
            'status': p['status'],
            'annotationProjectId': p['annotationProjectId'],
            'note': p['note'],
            'taskType': p['taskType'],
            'parentTaskId': p['parentTaskId'],
            'reviews': p['reviews'],
            'reviewStatus': p['reviewStatus'],
            'priorityScore': p['priorityScore']
        }

        return {'id': feature['id'], 'properties': properties, 'geometry': feature['geometry'], 'type': 'Feature'}

    def transform_task_grid(task_grid):
        task_grid_geojson = json.loads(task_grid_with_scores.to_json())
        task_grid_geojson['features'] = [_transform_geojson_feature(
            f) for f in task_grid_geojson['features']]

        return task_grid_geojson

    persist_hitl_output(project_id, transform_task_grid(task_grid_gdf),
                        json.loads(machine_labeled_preds.to_json()))

    # STEP 5 Notify Intercom

    # STEP 6 Update batch job status
