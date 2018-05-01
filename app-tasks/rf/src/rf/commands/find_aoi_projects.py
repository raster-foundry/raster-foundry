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
    project_ids = find_aoi_projects_to_update()
    kickoff_aoi_project_update_checks(project_ids)


def find_aoi_projects_to_update():
    """Find AOI projects to check for updates and return their IDs to process"""
    logger.info('Finding AOI projects to check for updates')

    bash_cmd = ['java', '-cp', '/opt/raster-foundry/jars/rf-batch.jar',
                'com.azavea.rf.batch.Main', 'find_aoi_projects']
    cmd = subprocess.Popen(bash_cmd, stdout=subprocess.PIPE)
    (stdout, stderr) = cmd.communicate()
    # Note: this is _extremely_ brittle, and relies on specific strings being printed
    # somewhere where the scala compiler doesn't know it needs to care about a message's text
    # See #3263
    projects = [
        x.strip().lstrip('Project to update: ') for x in stdout.split('\n') if
        x.startswith('Project to update: ')
    ]
    return projects


def kickoff_aoi_project_update_checks(project_ids):
    """Kick off updates for AOI projects that need to be updated

    Args:
        project_ids (List[str]): list of project ids to process
    """
    logger.info('Found projects to check for updates: %s', project_ids)

    if ENVIRONMENT.lower() not in ['staging', 'production']:
        logger.warn('Skipping kicking off AOI updates because in environment %s', ENVIRONMENT)
        return

    client = boto3.client('batch')

    for project_id in project_ids:
        parameters = {'projectId': project_id}
        client.submit_job(jobName=BATCH_JOB_NAME_AOI_UPDATE,
                          jobQueue=BATCH_QUEUE_AOI_UPDATE,
                          jobDefinition=BATCH_JOB_NAME_AOI_UPDATE,
                          parameters=parameters)
