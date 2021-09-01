# -*- coding: utf-8 -*-

"""Console script for Raster Foundry"""

import logging

import click

from .commands import process_upload

logger = logging.getLogger("rf")


@click.group()
@click.option("--verbose/--quiet")
def run(verbose):
    """Console script for raster_foundry_batch_tasks."""
    if verbose:
        logger.setLevel(logging.DEBUG)
        logger.debug("VERBOSE logging enabled")
    else:
        logger.setLevel(logging.INFO)


run.add_command(process_upload)
