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


@click.command(name='export')
@click.argument('export_id')
@wrap_rollbar
def export(export_id):
    """Perform export configured by user

    Args:
        export_id (str): ID of export job to process
    """
    export_uri = create_export_definition(export_id)
    try:
        status_uri = run_export(export_uri, export_id)
    finally:
        wait_for_status(export_id, status_uri)


def create_export_definition(export_id):
    """Create an export definition

    Args:
        export_id (str): ID of export job to process
    """
    bucket = os.getenv('DATA_BUCKET')
    key = 'export-definitions/{}'.format(export_id)
    logger.info('Creating export definition for %s', export_id)
    command = ['java', '-cp',
               '/opt/raster-foundry/jars/rf-batch.jar',
               'com.azavea.rf.batch.Main', 'create_export_def',
               export_id, bucket, key]
    logger.info('Running Command %s', ' '.join(command))
    subprocess.check_call(command)

    logger.info('Created export definition for %s', export_id)
    return 's3://{}/{}'.format(bucket, key)


def run_export(export_s3_uri, export_id):
    """Run spark export

    Args:
        export_s3_uri (str): location of job definition
        export_id (str): ID of export job to process

    """
    export_cores = os.getenv('LOCAL_INGEST_CORES', 32)
    export_memory = os.getenv('LOCAL_INGEST_MEM_GB', 48)
    status_uri = 's3://{}/export-statuses/{}'.format(os.getenv('DATA_BUCKET'), export_id)

    command = ['spark-submit',
               '--master', 'local[{}]'.format(export_cores),
               '--driver-memory', '{}g'.format(export_memory),
               '--class', 'com.azavea.rf.batch.export.spark.Export',
               '/opt/raster-foundry/jars/rf-batch.jar',
               '-j', export_s3_uri, '-b', status_uri
    ]
    subprocess.check_call(command)
    logger.info('Finished exporting %s in spark local', export_s3_uri)
    return status_uri


def wait_for_status(export_id, status_uri):
    """Wait for a result from the Spark job

    Args:
        status_uri (str): location of job status URI
        export_id (str): run command to wait for status of export
    """

    bash_cmd = ['java', '-cp', '/opt/raster-foundry/jars/rf-batch.jar',
                'com.azavea.rf.batch.Main',
                'check_export_status',
                export_id, status_uri]

    logger.info('Updating %s\'s export status after successful EMR status', export_id)
    subprocess.check_call(bash_cmd)
    logger.info('Successfully completed export %s', export_id)
