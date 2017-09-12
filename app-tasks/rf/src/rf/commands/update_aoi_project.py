import logging
import subprocess

import click

logger = logging.getLogger(__name__)


@click.command(name='update-aoi-project')
@click.argument('project_id')
def update_aoi_project(project_id):
    """Search for and add any new scenes to a given project

    Args:
        project_id (str): ID of project to check for new scenes to add
    """

    bash_cmd = 'java -cp /opt/raster-foundry/jars/rf-batch.jar com.azavea.rf.batch.Main update_aoi_project {0}'.format(project_id)

    exit_code = subprocess.call([bash_cmd], shell=True)
    logger.info('Checking whether %s has updated scenes available', project_id)
    is_success = exit_code == 0

    if is_success:
        logger.info('Successfully completed project %s update', project_id)
    else:
        raise Exception('Update of project %s failed', project_id)
    return is_success
