import datetime
import logging
import os
import time

from airflow.operators.python_operator import PythonOperator
from airflow.exceptions import AirflowException
from airflow.models import DAG
import boto3
import dns.resolver

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
hosted_zone_id = os.getenv('HOSTED_ZONE_ID')
jar_path = os.getenv('BATCH_JAR_PATH', 'rf-batch-ba1b872.jar')


################################
# Utility functions            #
################################

@wrap_rollbar
def get_cluster_id():
    resolver = dns.resolver.Resolver()
    cluster_id = resolver.query("dataproc.rasterfoundry.com", "TXT")[0]
    return cluster_id.to_text().strip('"')


@wrap_rollbar
def get_batch_jar():
    bucket = 'rasterfoundry-global-artificats-us-east-1'
    key = 'batch/{}'.format(jar_path)
    s3 = boto3.client('s3')
    resp = s3.get_object(Bucket=bucket, Key=key)
    with open('rf-batch.jar', 'wb') as outf:
        outf.write(resp['Body'].read())
    return True


@wrap_rollbar
def execute_ingest_emr_job(ingest_s3_uri, ingest_def_id, cluster_id):
    """Kick off ingest in AWS EMR

    Args:
        ingest_s3_uri (str): URI for ingest definition
        ingest_def_id (str): ID to namespace ingest job

    Returns:
        dict
    """
    step = {
        'ActionOnFailure': 'CONTINUE',
        'Name': 'ingest-{}'.format(ingest_def_id),
        'HadoopJarStep': {
            'Args': ['/usr/bin/spark-submit',
                     '--master',
                     'yarn',
                     '--deploy-mode',
                     'cluster',
                     '--class',
                     'com.azavea.rf.batch.ingest.Ingest',
                     '--driver-memory', '8G',
                     's3://rasterfoundry-global-artifacts-us-east-1/batch/{}'.format(jar_path),
                     '-t',
                     '-j',
                     ingest_s3_uri],
            'Jar': 's3://us-east-1.elasticmapreduce/libs/script-runner/script-runner.jar'
        }
    }
    logger.info('Step: %s', step)
    emr = boto3.client('emr')
    response = emr.add_job_flow_steps(
        JobFlowId=cluster_id,
        Steps=[step]
    )
    return response


@wrap_rollbar
def wait_for_success(response, cluster_id):
    """Wait for batch success/failure given an initial batch response

    Args:
        response (dict): AWS batch response object

    Returns:
        boolean
    """
    step_id = response['StepIds'][0]
    emr = boto3.client('emr')
    get_description = lambda: emr.describe_step(ClusterId=cluster_id, StepId=step_id)
    logger.info('Starting to check for status updates for step %s', step_id)
    step_description = get_description()
    current_status = step_description['Step']['Status']['State']
    logger.info('Initial status: %s', current_status)
    while current_status not in ['COMPLETED', 'FAILED']:
        description = get_description()
        status = description['Step']['Status']['State']
        if status != current_status:
            logger.info('Updating status of %s. Old Status: %s New Status: %s',
                        step_id, current_status, status)
            current_status = status
        time.sleep(30)
    is_success = (current_status == 'COMPLETED')
    if is_success:
        logger.info('Successfully completed ingest for %s', step_id)
        return True
    else:
        logger.error('Something went wrong with %s. Current Status: %s', step_id, current_status)
        raise AirflowException('Ingest failed for {}'.format(step_id))


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
    cluster_id = get_cluster_id()
    emr_response = execute_ingest_emr_job(ingest_def_uri, ingest_def_id, cluster_id)
    logger.info('Finished launching Spark ingest job. Waiting for status changes.')
    is_success = wait_for_success(emr_response, cluster_id)
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
