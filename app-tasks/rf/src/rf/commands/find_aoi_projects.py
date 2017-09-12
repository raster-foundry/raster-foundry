import logging
import os
import subprocess

import click
import boto3

logger = logging.getLogger(__name__)

ENVIRONMENT = os.getenv('ENVIRONMENT').title()
AWS_BATCH_JOB_NAME_AOI_UPDATE = os.getenv('AWS_BATCH_JOB_NAME_AOI_UPDATE')
AWS_BATCH_QUEUE_AOI_UPDATE = os.getenv('AWS_BATCH_QUEUE_AOI_UPDATE', 'queue{}Default'.format(ENVIRONMENT))


@click.command(name='find-aoi-projects')
def find_aoi_projects():
    """Find AOI projects that need to be checked for updates and kick off jobs to update"""
    click.echo("Finding AOI projects")
    project_ids = find_aoi_projects_to_update()
    kickoff_aoi_project_update_checks(project_ids)


def find_aoi_projects_to_update():
    """Find AOI projects to check for updates and return their IDs to process"""
    logger.info('Finding AOI projects to check for updates')

    bash_cmd = 'java -cp /opt/raster-foundry/jars/rf-batch.jar com.azavea.rf.batch.Main find_aoi_projects'
    cmd = subprocess.Popen(bash_cmd, shell=True, stdout=subprocess.PIPE)
    projects = ['']
    for line in cmd.stdout:
        logger.info(line.strip())
        if 'ProjectIds:' in line:
            projects = [p.strip() for p in line.replace('ProjectIds:', '').strip().split(',')]
    return projects


def kickoff_aoi_project_update_checks(project_ids):
    """Kick off updates for AOI projects that need to be updated

    Args:
        project_ids (List[str]): list of project ids to process
    """

    if ENVIRONMENT.lower() not in ['staging', 'production']:
        logger.warn('Skipping kicking off AOI updates because in environment %s', ENVIRONMENT)
        return

    client = boto3.client('batch')
    logger.info('Found projects to check for updates: %s', project_ids)

    for project_id in project_ids:
        parameters = {'project': project_id}
        client.submit_job(jobName=AWS_BATCH_JOB_NAME_AOI_UPDATE,
                          jobQueue=AWS_BATCH_QUEUE_AOI_UPDATE,
                          jobDefinition=AWS_BATCH_JOB_NAME_AOI_UPDATE,
                          parameters=parameters)
