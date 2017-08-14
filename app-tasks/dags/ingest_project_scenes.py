import datetime
import logging
import os
import random
import time

from airflow.operators.python_operator import PythonOperator
from airflow.exceptions import AirflowException
from airflow.models import DAG
import subprocess
import boto3
import dns.resolver

from rf.utils.io import IngestStatus
from rf.models import Scene
from rf.ingest.models import Ingest
from rf.ingest import create_landsat8_ingest, create_ingest_definition
from rf.uploads.landsat8.settings import datasource_id as landsat_id
from rf.utils.exception_reporting import wrap_rollbar

from utils import failure_callback


formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
ch = logging.StreamHandler()

ch.setLevel(logging.INFO)
ch.setFormatter(formatter)

logging.getLogger('rf').addHandler(ch)

logger = logging.getLogger(__name__)


default_args = {
    'owner': 'raster-foundry',
    'start_date': datetime.datetime(2017, 1, 1)
}


dag = DAG(
    dag_id='ingest_scene',
    default_args=default_args,
    schedule_interval=None,
    concurrency=int(os.getenv('AIRFLOW_DAG_CONCURRENCY', 24)),
    dagrun_timeout=datetime.timedelta(minutes=60)
)


batch_job_definition = os.getenv('BATCH_INGEST_JOB_NAME')
batch_job_queue = os.getenv('BATCH_INGEST_JOB_QUEUE')
hosted_zone_id = os.getenv('HOSTED_ZONE_ID')
jar_path = os.getenv('BATCH_JAR_PATH', 'rf-batch-761c316.jar')


################################
# Utility functions            #
################################

@wrap_rollbar
def get_cluster_id():
    resolver = dns.resolver.Resolver()
    cluster_id = resolver.query("dataproc.rasterfoundry.com", "TXT")[0]
    return cluster_id.to_text().strip('"')


@wrap_rollbar
def execute_ingest_emr_job(scene_id, ingest_s3_uri, ingest_def_id, cluster_id):
    """Kick off ingest in AWS EMR

    Args:
        scene_id (str): id of the scene to ingest
        ingest_s3_uri (str): URI for ingest definition
        ingest_def_id (str): ID to namespace ingest job
        cluster_id (str): ID of the cluster to submit work to

    Returns:
        dict
    """
    logger.info('Constructing ingest step request for %s for cluster %s for ingest id %s',
                scene_id, cluster_id, ingest_def_id)
    step = {
        'ActionOnFailure': 'CONTINUE',
        'Name': 'ingest-{}'.format(ingest_def_id),
        'HadoopJarStep': {
            'Args': ['/usr/bin/spark-submit',
                     '--master',
                     'yarn',
                     '--deploy-mode',
                     'cluster',
                     '--conf',
                     'spark.yarn.submit.waitAppCompletion=false',
                     '--class',
                     'com.azavea.rf.batch.ingest.spark.Ingest',
                     's3://rasterfoundry-global-artifacts-us-east-1/batch/{}'.format(jar_path),
                     '-t',
                     '--overwrite',
                     '-s',
                     scene_id,
                     '-j',
                     ingest_s3_uri],
            'Jar': 's3://us-east-1.elasticmapreduce/libs/script-runner/script-runner.jar'
        }
    }
    logger.info('Submitting step to EMR (%s)', step)
    emr = boto3.client('emr')
    response = emr.add_job_flow_steps(
        JobFlowId=cluster_id,
        Steps=[step]
    )
    logger.info('Received response from EMR API: %s', response)
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
        time.sleep(45 + random.randint(0, 30))
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

    conf = kwargs['dag_run'].conf
    xcom_client = kwargs['task_instance']

    scene_id = conf.get('sceneId')
    xcom_client.xcom_push(key='ingest_scene_id', value=scene_id)
    scene = Scene.from_id(scene_id)

    logger.info('Beginning to create ingest definition for scene %s for user %s...',
                scene_id, scene.owner)

    if scene.ingestStatus != IngestStatus.TOBEINGESTED and scene.ingestStatus != IngestStatus.FAILED:
        raise Exception('Scene is no longer waiting to be ingested, error error')

    scene.ingestStatus = IngestStatus.INGESTING
    logger.info('Updating scene (%s) status to ingesting', scene_id)
    scene.update()
    logger.info('Successfully updated scene (%s) status', scene_id)

    logger.info('Creating ingest definition')
    if scene.datasource != landsat_id:
        ingest_definition = create_ingest_definition(scene)
    else:
        ingest_definition = create_landsat8_ingest(scene)
    ingest_definition.put_in_s3()
    logger.info('Successfully created and pushed ingest definition for scene %s', scene_id)

    # Store values for later tasks
    xcom_client.xcom_push(key='ingest_def_uri', value=ingest_definition.s3_uri)
    xcom_client.xcom_push(key='ingest_def_id', value=ingest_definition.id)
    xcom_client.xcom_push(key='scene_id', value=scene.id)


@wrap_rollbar
def launch_spark_ingest_job_op(*args, **kwargs):
    """Launch ingest job and wait for success/failure"""
    xcom_client = kwargs['task_instance']
    ingest_def_uri = xcom_client.xcom_pull(key='ingest_def_uri', task_ids=None)
    ingest_def_id = xcom_client.xcom_pull(key='ingest_def_id', task_ids=None)
    scene_id = xcom_client.xcom_pull(key='scene_id', task_ids=None)

    logger.info('Launching Spark ingest job with ingest definition %s for scene %s',
                ingest_def_uri, scene_id)
    cluster_id = get_cluster_id()
    emr_response = execute_ingest_emr_job(scene_id, ingest_def_uri, ingest_def_id, cluster_id)
    logger.info('LaunchedSpark ingest job for %s with ingest ID %s. Waiting for status changes.',
                scene_id, ingest_def_id)
    is_success = wait_for_success(emr_response, cluster_id)
    return is_success


@wrap_rollbar
def wait_for_status_op(*args, **kwargs):
    """Wait for a result from the Spark job"""
    xcom_client = kwargs['task_instance']
    ingest_def_id = xcom_client.xcom_pull(key='ingest_def_id', task_ids=None)
    ingest_status_dict = Ingest.get_status_from_s3(ingest_def_id)
    scene_id = ingest_status_dict['sceneId']
    status = ingest_status_dict['ingestStatus']

    layer_s3_bucket = os.getenv('TILE_SERVER_BUCKET')
    s3_output_location = 's3://{}/layers'.format(layer_s3_bucket)

    logger.info('Waiting for scene status at %s for scene %s with ingest defintion %s',
                s3_output_location, scene_id, ingest_def_id)

    scene = Scene.from_id(scene_id)
    scene.ingestLocation = s3_output_location
    scene.ingestStatus = status

    if scene.ingestStatus != IngestStatus.FAILED:
        logger.info('Writing scene metadata into postgres.')
        metadata_to_postgres(s3_output_location, scene_id)

    logger.info('Setting scene %s ingest status to %s', scene.id, scene.ingestStatus)
    scene.update()
    logger.info('Successfully updated scene %s\'s ingest status', scene.id)

    if scene.ingestStatus == IngestStatus.FAILED:
        raise AirflowException('Failed to ingest {} for user {}'.format(scene_id, scene.owner))

@wrap_rollbar
def metadata_to_postgres(uri, scene_id):
    bash_cmd = [
        'java',
        '-cp',
        '/opt/raster-foundry/jars/rf-batch.jar',
        'com.azavea.rf.batch.Main',
        'migration_s3_postgres',
        uri,
        'layer_attributes',
        scene_id
    ]
    logger.info('Bash command to store metadata: %s', ' '.join(bash_cmd))
    cmd = subprocess.check_call(bash_cmd, stdout=subprocess.PIPE)
    logger.info('Successfully completed metadata postgres write for scene %s', scene_id)
    return True

################################
# Tasks                        #
################################
create_ingest_definition_task = PythonOperator(
    task_id='create_ingest_definition',
    provide_context=True,
    python_callable=create_ingest_definition_op,
    on_failure_callback=failure_callback,
    dag=dag
)


launch_spark_ingest_task = PythonOperator(
    task_id='launch_spark_ingest',
    provide_context=True,
    python_callable=launch_spark_ingest_job_op,
    on_failure_callback=failure_callback,
    dag=dag
)

wait_for_status_task = PythonOperator(
    task_id='wait_for_status',
    provide_context=True,
    python_callable=wait_for_status_op,
    on_failure_callback=failure_callback,
    dag=dag
)


################################
# DAG Structure Specification  #
################################
launch_spark_ingest_task.set_upstream(create_ingest_definition_task)
wait_for_status_task.set_upstream(launch_spark_ingest_task)
