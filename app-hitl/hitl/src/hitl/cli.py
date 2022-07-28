# -*- coding: utf-8 -*-

"""Console script for Raster Foundry"""

import logging

import click

from .commands import run_hitl

logger = logging.getLogger("hitl")


@click.group()
@click.option("--verbose/--quiet")
def run(verbose):
    """Console script for raster_foundry_batch_hitl_tasks."""
    if verbose:
        logger.setLevel(logging.DEBUG)
        logger.debug("VERBOSE logging enabled")
    else:
        logger.setLevel(logging.INFO)


run.add_command(run_hitl)
