import logging
import os
import subprocess

import click
import boto3

from ..utils.exception_reporting import wrap_rollbar

logger = logging.getLogger(__name__)

ENVIRONMENT = os.getenv('ENVIRONMENT').title()
BATCH_JOB_NAME_AOI_UPDATE = os.getenv('BATCH_JOB_NAME_AOI_UPDATE')
BATCH_QUEUE_AOI_UPDATE = os.getenv('BATCH_QUEUE_AOI_UPDATE', 'queue{}Default'.format(ENVIRONMENT))


@click.command(name='find-aoi-projects')
@wrap_rollbar
def find_aoi_projects():
    """Find AOI projects that need to be checked for updates and kick off jobs to update"""
    click.echo("Finding AOI projects")
    find_aoi_projects_to_update()

def find_aoi_projects_to_update():
    """Find AOI projects to check for updates and kick off update batch jobs"""
    logger.info('Finding AOI projects to check for updates')

    bash_cmd = ['java', '-cp', '/opt/raster-foundry/jars/rf-batch.jar',
                'com.azavea.rf.batch.Main', 'find_aoi_projects']
    subprocess.check_call(bash_cmd)
