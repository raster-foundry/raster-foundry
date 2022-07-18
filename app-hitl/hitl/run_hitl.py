import logging
import os

import click
import geopandas as gpd

from utils.merge_labels import merge_labels_with_task_grid

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
    pass

if __name__ == '__main__':
    run()
