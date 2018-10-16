import logging
import subprocess

import click

from ..utils.exception_reporting import wrap_rollbar

logger = logging.getLogger(__name__)


@click.command(name='update-aoi-project')
@click.argument('project_id')
@wrap_rollbar
def update_aoi_project(project_id):
    """Search for and add any new scenes to a given project

    Args:
        project_id (str): ID of project to check for new scenes to add
    """

    bash_cmd = [
        'java', '-cp', '/opt/raster-foundry/jars/batch-assembly.jar',
        'com.rasterfoundry.batch.Main', 'update_aoi_project', project_id
    ]

    try:
        subprocess.check_call(bash_cmd)
    except subprocess.CalledProcessError as e:
        logger.info('Command %s failed.\n Output is: %s.\n Stderr is: %s',
                    e.cmd, e.output, e.stderr)
        raise e
    return True
