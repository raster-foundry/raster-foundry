import datetime
import logging
import os
import time

from airflow.operators.python_operator import PythonOperator
from airflow.exceptions import AirflowException
from airflow.models import DAG
import boto3

from rf.utils.io import IngestStatus
from rf.models import Scene
from rf.ingest import create_landsat8_ingest, create_ingest_definition
from rf.uploads.landsat8.settings import datasource_id as landsat_id
from rf.utils.exception_reporting import wrap_rollbar

rf_logger = logging.getLogger('rf')
ch = logging.StreamHandler()
ch.setLevel(logging.INFO)
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
ch.setFormatter(formatter)
rf_logger.addHandler(ch)

logger = logging.getLogger(__name__)

default_args = {
    'owner': 'raster-foundry',
    'start_date': datetime.datetime(2017, 1, 1)
}


dag = DAG(
    dag_id='ingest_project_scenes',
    default_args=default_args,
    schedule_interval=None,
    concurrency=int(os.getenv('AIRFLOW_DAG_CONCURRENCY', 4))
)


batch_job_definition = os.getenv('BATCH_INGEST_JOB_NAME')
batch_job_queue = os.getenv('BATCH_INGEST_JOB_QUEUE')


################################
# Utility functions            #
################################

@wrap_rollbar
def get_latest_batch_job_revision():
    """Get latest batch job definition ARN

    This function is used to get the lates AWS batch ARN to submit an ingest job to
    """
    batch = boto3.client('batch')
    job_definitions = batch.describe_job_definitions(jobDefinitionName=batch_job_definition)['jobDefinitions']
    latest_definition = sorted(job_definitions, key=lambda job: job['revision'], reverse=True)[0]
    return latest_definition['jobDefinitionArn']


@wrap_rollbar
def execute_ingest_batch_job(ingest_s3_uri, ingest_def_id):
    """Kick off ingest in AWS Batch

    Args:
        ingest_s3_uri (str): URI for ingest definition
        ingest_def_id (str): ID to namespace ingest job

    Returns:
        dict
    """
    batch = boto3.client('batch')
    ingest_arn = get_latest_batch_job_revision()
    response = batch.submit_job(
        jobName='ingest-{}'.format(ingest_def_id),
        jobQueue=batch_job_queue,
        jobDefinition=ingest_arn,
        parameters={
            'ingest_definition': ingest_s3_uri
        }
    )
    return response


@wrap_rollbar
def wait_for_success(response):
    """Wait for batch success/failure given an initial batch response

    Args:
        response (dict): AWS batch response object

    Returns:
        boolean
    """
    job_id = response['jobId']
    job_name = response['jobName']

    batch = boto3.client('batch')
    get_description = lambda: batch.describe_jobs(jobs=[job_id])['jobs'][0]
    logger.info('Starting to check for status updates for job %s', job_name)
    job_description = get_description()
    current_status = job_description['status']
    logger.info('Initial status: %s', current_status)
    while current_status not in ['SUCCEEDED', 'FAILED']:
        description = get_description()
        status = description['status']
        if status != current_status:
            logger.info('Updating status of %s. Old Status: %s New Status: %s',
                        job_name, current_status, status)
            current_status = status
        time.sleep(15)
    is_success = (current_status == 'SUCCEEDED')
    if is_success:
        logger.info('Successfully completed ingest for %s', job_name)
        return True
    else:
        logger.error('Something went wrong with %s. Current Status: %s', job_name, current_status)
        raise AirflowException('Ingest failed for {}'.format(job_name))


################################
# Callables for PythonOperators#
################################

@wrap_rollbar
def create_ingest_definition_op(*args, **kwargs):
    """Create ingest definition and upload to S3"""

    logger.info('Beginning to create ingest definition...')
    xcom_client = kwargs['task_instance']
    conf = kwargs['dag_run'].conf
    scene_dict = conf.get('scene')
    xcom_client.xcom_push(key='ingest_scene_id', value=scene_dict['id'])
    scene = Scene.from_id(scene_dict['id'])

    if scene.ingestStatus != IngestStatus.TOBEINGESTED:
        raise Exception('Scene is no longer waiting to be ingested, error error')

    scene.ingestStatus = IngestStatus.INGESTING
    logger.info('Updating scene status to ingesting')
    scene.update()
    logger.info('Successfully updated scene status')

    logger.info('Creating ingest definition')
    if scene.datasource != landsat_id:
        ingest_definition = create_ingest_definition(scene)
    else:
        ingest_definition = create_landsat8_ingest(scene)
    ingest_definition.put_in_s3()
    logger.info('Successfully created and pushed ingest definition')

    # Store values for later tasks
    xcom_client.xcom_push(key='ingest_def_uri', value=ingest_definition.s3_uri)
    xcom_client.xcom_push(key='ingest_def_id', value=ingest_definition.id)


@wrap_rollbar
def launch_spark_ingest_job_op(*args, **kwargs):
    """Launch ingest job and wait for success/failure"""
    xcom_client = kwargs['task_instance']
    ingest_def_uri = xcom_client.xcom_pull(key='ingest_def_uri', task_ids=None)
    ingest_def_id = xcom_client.xcom_pull(key='ingest_def_id', task_ids=None)

    logger.info('Launching Spark ingest job with ingest definition %s', ingest_def_uri)
    batch_response = execute_ingest_batch_job(ingest_def_uri, ingest_def_id)
    logger.info('Finished launching Spark ingest job. Waiting for status changes.')
    is_success = wait_for_success(batch_response)
    return is_success


@wrap_rollbar
def set_ingest_status_success_op(*args, **kwargs):
    """Set scene ingest status on success"""
    xcom_client = kwargs['task_instance']
    scene_id = xcom_client.xcom_pull(key='ingest_scene_id', task_ids=None)
    logger.info("Setting scene (%s) ingested status to success", scene_id)
    scene = Scene.from_id(scene_id)
    scene.ingestStatus = IngestStatus.INGESTED

    layer_s3_bucket = os.getenv('TILE_SERVER_BUCKET')

    s3_output_location = 's3://{}/layers'.format(layer_s3_bucket)
    scene.ingestLocation = s3_output_location
    scene.update()
    logger.info("Finished setting scene (%s) ingest status (%s)", scene_id, IngestStatus.INGESTED)


@wrap_rollbar
def set_ingest_status_failure_op(*args, **kwargs):
    """Set ingest status on failure"""
    xcom_client = kwargs['task_instance']
    scene_id = xcom_client.xcom_pull(key='ingest_scene_id', task_ids=None)
    logger.info("Setting scene (%s) ingested status to failed", scene_id)
    scene = Scene.from_id(scene_id)
    scene.ingestStatus = IngestStatus.FAILED
    scene.update()
    logger.info("Finished setting scene (%s) ingest status (%s)", scene_id, IngestStatus.FAILED)


################################
# Tasks                        #
################################
create_ingest_definition_task = PythonOperator(
    task_id='create_ingest_definition',
    provide_context=True,
    python_callable=create_ingest_definition_op,
    dag=dag
)


launch_spark_ingest_task = PythonOperator(
    task_id='launch_spark_ingest',
    provide_context=True,
    python_callable=launch_spark_ingest_job_op,
    dag=dag
)


set_ingest_status_success_task = PythonOperator(
    task_id='set_ingest_status_success',
    python_callable=set_ingest_status_success_op,
    provide_context=True,
    trigger_rule='all_success',
    dag=dag
)


set_ingest_status_failure_task = PythonOperator(
    task_id='set_ingest_status_failure',
    python_callable=set_ingest_status_failure_op,
    provide_context=True,
    trigger_rule='all_failed',
    dag=dag
)


################################
# DAG Structure Specification  #
################################
set_ingest_status_success_task.set_upstream(launch_spark_ingest_task)
set_ingest_status_failure_task.set_upstream(launch_spark_ingest_task)
launch_spark_ingest_task.set_upstream(create_ingest_definition_task)
