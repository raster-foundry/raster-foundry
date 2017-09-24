import logging
import os
import subprocess
import time

import click

from ..utils.exception_reporting import wrap_rollbar
from ..utils.emr import get_cluster_id, wait_for_emr_success

logger = logging.getLogger(__name__)

HOST = os.getenv('RF_HOST')
API_PATH = '/api/exports/'


@click.command()
@click.argument('export_id')
@wrap_rollbar
def export(export_id):
    """Perform export configured by user

    Args:
        export_id (str): ID of export job to process
    """
    start_emr_export(export_id)
    wait_for_status(export_id)


def start_emr_export(export_id):
    """Start process of EMR export

    Args:
        export_id (str): ID of export object
    """
    bash_cmd = 'java -cp /opt/raster-foundry/jars/rf-batch.jar com.azavea.rf.batch.Main create_export_def {0}'.format(export_id)
    cmd = subprocess.Popen(bash_cmd, shell=True, stdout=subprocess.PIPE)
    step_id = ''
    for line in cmd.stdout:
        logger.info(line.strip())
        if 'StepId:' in line:
            step_id = line.replace('StepId:', '').strip()

    logger.info('Launched export creation process, watching for updates...')
    is_success = wait_for_emr_success(step_id, get_cluster_id())
    return is_success


def wait_for_status(export_id):
    """Wait for a result from the Spark job

    Args:
        export_id (str): run command to wait for status of export
    """

    bash_cmd = 'java -cp /opt/raster-foundry/jars/rf-batch.jar com.azavea.rf.batch.Main check_export_status {0}'.format(export_id)
    logger.info('Updating %s\'s export status after successful EMR status', export_id)
    cmd = subprocess.Popen(bash_cmd, shell=True, stdout=subprocess.PIPE)
    for line in cmd.stdout:
        logger.info(line.strip())

    # Wait until process terminates (without using cmd.wait())
    while cmd.poll() is None:
        time.sleep(0.5)

    if cmd.returncode == 0:
        logger.info('Successfully completed export %s', export_id)
        return True
    else:
        logger.error('Something went wrong with export: %s', export_id)
        raise Exception('Export failed for {}'.format(export_id))
