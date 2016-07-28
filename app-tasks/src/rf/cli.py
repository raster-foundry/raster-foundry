"""Module that contains the command line app."""

import os

import boto3

from botocore.session import Session
from botoflow.workers.activity_worker import ActivityWorker
from botoflow.workers.workflow_worker import WorkflowWorker
from botoflow.workflow_starting import workflow_starter

import click

from .layer_uploads import (
    LayerImageUploadActivities,
    LayerImageUploadWorkflow
)
from .utils.logger import get_logger

_shared_options = [
    click.option('--region', default='us-east-1', help='Region to pull SWF activities from'),
    click.option('--swf-domain', default=lambda: os.environ.get('AWS_SWF_DOMAIN'), help='SWF Domain to schedule jobs with'),
    click.option('--task-list', default='default', help='Task list to schedule tasks with')
]


def shared_options(func):
    for option in reversed(_shared_options):
        func = option(func)
    return func

logger = get_logger()


@click.group()
def cli():
    click.secho("Welcome to Raster Foundry", fg='green')


@cli.command()
@shared_options
def decider(region, swf_domain, task_list):
    """Start workflow decider"""
    click.secho('Starting Decider Worker', fg='green')    
    session = Session()
    workflow_worker = WorkflowWorker(session, region, swf_domain, task_list, LayerImageUploadWorkflow)
    workflow_worker.run()


@cli.command()
@shared_options
def worker(region, swf_domain, task_list):
    """Start activity worker"""
    click.secho('Starting Activity Worker', fg='green')
    session = Session()
    activity_worker = ActivityWorker(session, region, swf_domain, task_list, *[LayerImageUploadActivities()])
    activity_worker.run()


@cli.command()
@click.argument('image_path')    
@shared_options
def process_image(image_path, region, swf_domain, task_list):
    """Kick off workflow to process image"""
    
    click.secho('Kicking off SWF Workflow to process image: {}'.format(image_path), fg='green')
    session = Session()
    with workflow_starter(session, region, swf_domain, task_list):
        LayerImageUploadWorkflow.process_layer(image_path)


@cli.command()
@shared_options
def register_domain(region, swf_domain, task_list):
    """Potentially registers SWF domain"""
    client = boto3.client('swf')
    domains = client.list_domains(
        registrationStatus='REGISTERED',
        maximumPageSize=123,
        reverseOrder=False
    )

    domain = next((domain for domain in domains['domainInfos'] if domain['name'] == swf_domain), [])
    if domain:
        click.secho('SWF Domain {} already exists, not registering.'.format(swf_domain), fg='yellow')
    else:
        click.secho('Registering SWF Domain: {}'.format(swf_domain), fg='green')
        client.register_domain(
            name=swf_domain,
            workflowExecutionRetentionPeriodInDays='28'
        )
