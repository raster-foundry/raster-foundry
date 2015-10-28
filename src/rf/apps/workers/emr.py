# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import logging

import boto3
from django.conf import settings

from apps.core.models import LayerImage


log = logging.getLogger(__name__)


def create_cluster(layer):
    """
    Create new EMR cluster.
    """
    queue_name = settings.AWS_SQS_QUEUE
    artifacts = settings.AWS_ARTIFACTS_BUCKET

    client = boto3.client('emr')
    status_queue = get_queue_url(queue_name)

    logs_uri = 's3://{}'.format(settings.AWS_LOGS_BUCKET)
    bootstrap_uri = 's3://{}/bootstrap.sh'.format(artifacts)

    instances = {
        'InstanceGroups': [
            {
                'Name': 'Master',
                'InstanceRole': 'MASTER',
                'InstanceType': 'm3.xlarge',
                'InstanceCount': 1,
            },
            {
                'Name': 'Workers',
                'InstanceRole': 'CORE',
                'InstanceType': 'm3.xlarge',
                'Market': 'ON_DEMAND',
                'InstanceCount': int(settings.AWS_EMR_INSTANCES),
            },
        ],
    }

    if settings.AWS_EMR_DEBUG:
        instances['Ec2KeyName'] = 'rf-emr'
        instances['KeepJobFlowAliveWhenNoSteps'] = True

    config_env_vars = {
        'Classification': 'export',
        'Properties': {
            'GDAL_DATA': '/usr/local/share/gdal',
            'LD_LIBRARY_PATH': '/usr/local/lib',
            'PYSPARK_PYTHON': 'python27',
            'PYSPARK_DRIVER_PYTHON': 'python27',
        },
    }

    configurations = [
        {
            'Classification': 'hadoop-env',
            'Configurations': [config_env_vars],
        },
        {
            'Classification': 'spark-env',
            'Configurations': [config_env_vars],
        },
        {
            'Classification': 'yarn-env',
            'Configurations': [config_env_vars],
        }
    ]

    response = client.run_job_flow(
        Name=settings.AWS_EMR_CLUSTER_NAME,
        LogUri=logs_uri,
        ReleaseLabel='emr-4.0.0',

        # These roles are created when you manually launch an EMR cluster
        # or by using the following command:
        # > aws emr create-default-roles
        ServiceRole='EMR_DefaultRole',
        JobFlowRole='EMR_EC2_DefaultRole',

        Applications=[
            {
                'Name': 'Spark',
            }
        ],
        BootstrapActions=[
            {
                'Name': 'Install dependencies',
                'ScriptBootstrapAction': {
                    'Path': bootstrap_uri,
                }
            }
        ],
        Instances=instances,
        Configurations=configurations,
        Steps=get_steps(layer, status_queue),
    )
    log.debug(response)
    return response


def check_cluster_status(cluster_id):
    alive_instance_statuses = [
        'PROVISIONING',
        'BOOTSTRAPPING',
        'RUNNING',
        'RESIZING',
    ]
    alive_step_statuses = [
        'PENDING',
        'RUNNING',
        'COMPLETED',
    ]
    try:
        client = boto3.client('emr')
        response = client.list_instance_groups(ClusterId=cluster_id)
        statuses = (instance['Status']['State']
                    for instance in response['InstanceGroups'])
        instances_alive = all(s in alive_instance_statuses for s in statuses)

        if instances_alive:
            response = client.list_steps(ClusterId=cluster_id)
            statuses = (step['Status']['State']
                        for step in response['Steps'])
            return all(s in alive_step_statuses for s in statuses)
        else:
            return False
    except:
        return False


def add_steps(layer, status_queue, cluster_id):
    """
    Add steps to an existing cluster.
    cluster_id - Existing EMR cluster ID
    """
    client = boto3.client('emr')
    response = client.add_job_flow_steps(
        JobFlowId=cluster_id,
        Steps=get_steps(layer, status_queue),
    )
    log.debug(response)


def get_steps(layer, status_queue):
    spark_submit = [
        'spark-submit',
        '--deploy-mode',
        'cluster',
        '--driver-memory',
        '2g',
        '--executor-memory',
        '2304m',
        '--executor-cores',
        '1',
        '--num-executors',
        str(4 * int(settings.AWS_EMR_INSTANCES)),
    ]

    workspace = 's3://{}'.format(settings.AWS_WORKSPACE_BUCKET)
    chunk_result = 's3://{}/{}.json'.format(settings.AWS_WORKSPACE_BUCKET,
                                            layer.id)
    chunk_uri = 's3://{}/chunk.py'.format(settings.AWS_ARTIFACTS_BUCKET)
    mosaic_uri = 's3://{}/mosaic.jar'.format(settings.AWS_ARTIFACTS_BUCKET)

    images = LayerImage.objects.filter(layer_id=layer.id)
    images = [image.get_s3_uri() for image in images]

    action_on_failure = 'CONTINUE' if settings.AWS_EMR_DEBUG \
                        else 'TERMINATE_CLUSTER'

    return [
        {
            'Name': 'Chunk',
            'ActionOnFailure': action_on_failure,
            'HadoopJarStep': {
                'Jar': 'command-runner.jar',
                'Args': spark_submit + [
                    chunk_uri,
                    '--job-id',
                    str(layer.id),
                    '--workspace',
                    workspace,
                    '--target',
                    layer.get_tile_bucket_path(),
                    '--output',
                    chunk_result,
                    '--status-queue',
                    status_queue,
                ] + images
            }
        },
        {
            'Name': 'Mosaic',
            'ActionOnFailure': action_on_failure,
            'HadoopJarStep': {
                'Jar': 'command-runner.jar',
                'Args': spark_submit + [
                    '--class',
                    'com.azavea.rasterfoundry.Main',
                    mosaic_uri,
                    '--status-queue',
                    status_queue,
                    chunk_result,
                ]
            }
        }
    ]


def get_queue_url(queue_name):
    """
    Return SQS URL for given `queue_name`. Assumes `queue_name` exists and
    is accessible by current profile context.
    """
    client = boto3.client('sqs')
    return client.get_queue_url(QueueName=queue_name)['QueueUrl']
