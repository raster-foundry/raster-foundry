# -*- coding: utf-8 -*-

"""Console script for Raster Foundry"""

import logging
import os

import click

from .commands import (
    export,
    find_aoi_projects,
    ingest_scene,
    process_upload,
    update_aoi_project
)

logger = logging.getLogger('rf')


if int(os.getenv('AWS_BATCH_JOB_ATTEMPT', '-1')) > 3:
    raise Exception('Failing async task early after suspicious repeated failures')


@click.group()
@click.option('--verbose/--quiet')
def run(verbose):
    """Console script for raster_foundry_batch_tasks."""
    if verbose:
        logger.setLevel(logging.DEBUG)
        logger.debug('VERBOSE logging enabled')
    else:
        logger.setLevel(logging.INFO)


run.add_command(export)
run.add_command(process_upload)
run.add_command(ingest_scene)
run.add_command(find_aoi_projects)
run.add_command(update_aoi_project)
