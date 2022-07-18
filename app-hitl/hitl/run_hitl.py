import json
import logging
import os

import click
import geopandas as gpd

from utils.merge_labels import merge_labels_with_task_grid
from utils.get_hitl_input import get_input
from utils.notify_intercom import notify

logger = logging.getLogger(__name__)
HOST = os.getenv("RF_HOST")
JOB_ATTEMPT = int(os.getenv("AWS_BATCH_JOB_ATTEMPT", -1))

@click.command(name="run")
@click.argument('job_id')
def run(job_id):
    """Run a HITL job to generate label predictions

    Args:
        job_id (str): ID of a HITL Job to run
    """
    # STEP 1 Get HITL input from API
    # - Enhancement later: save task and label to a file
    scene, tasks, labels, label_classes, job = get_input(job_id)

    # STEP 2 Train and predict with RV
    # * Output:
    #   - Labels in GeoJSON
    #   - Task with priority scores
    #   - Enhancement later: save task and label to files

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

    # STEP 4 Persist data to DB
    # - Update tasks (PUT)
    # - Add prediction labels (POST)

    # STEP 5 Notify Intercom

if __name__ == '__main__':
    run()
