from __future__ import print_function
import datetime
import logging
import os
import time

import subprocess
import dns.resolver
import boto3

from airflow.models import DAG
from airflow.operators.python_operator import PythonOperator
from airflow.exceptions import AirflowException
from rf.utils.exception_reporting import wrap_rollbar

from utils import failure_callback


formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
ch = logging.StreamHandler()

ch.setLevel(logging.INFO)
ch.setFormatter(formatter)

logging.getLogger('rf').addHandler(ch)

logger = logging.getLogger(__name__)


HOST = os.getenv('RF_HOST')
API_PATH = '/api/exports/'

logger = logging.getLogger(__name__)

default_args = {
    'owner': 'raster-foundry',
    'start_date': datetime.datetime(2017, 1, 1)
}

dag = DAG(
    dag_id='export_project',
    default_args=default_args,
    schedule_interval=None,
    concurrency=os.getenv('AIRFLOW_DAG_CONCURRENCY', 4)
)

# UTILS
@wrap_rollbar
def get_cluster_id():
    resolver = dns.resolver.Resolver()
    cluster_id = resolver.query("dataproc.rasterfoundry.com", "TXT")[0]
    return cluster_id.to_text().strip('"')

@wrap_rollbar
def start_export(export_id, xcom_client):
    bash_cmd = 'java -cp /opt/raster-foundry/jars/rf-batch.jar com.azavea.rf.batch.Main create_export_def {0}'.format(export_id)
    cmd = subprocess.Popen(bash_cmd, shell=True, stdout=subprocess.PIPE)
    step_id = ''
    for line in cmd.stdout:
        logger.info(line.strip())
        if 'StepId:' in line:
            step_id = line.replace('StepId:', '').strip()

    xcom_client.xcom_push(key='export_id', value=export_id)
    logger.info('Launched export creation process, watching for updates...')
    is_success = wait_for_success(step_id, get_cluster_id())

    return is_success

@wrap_rollbar
def wait_for_success(step_id, cluster_id):
    """Wait for batch success/failure given an initial batch response

    Returns:
        boolean
    """
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
        logger.info('Successfully completed export for %s', step_id)
        return True
    else:
        logger.error('Something went wrong with %s. Current Status: %s', step_id, current_status)
        raise AirflowException('Export failed for {}'.format(step_id))

@wrap_rollbar
def wait_for_status_op(*args, **kwargs):
    """Wait for a result from the Spark job"""
    xcom_client = kwargs['task_instance']
    export_id = xcom_client.xcom_pull(key='export_id', task_ids=None)

    bash_cmd = 'java -cp /opt/raster-foundry/jars/rf-batch.jar com.azavea.rf.batch.Main check_export_status {0}'.format(export_id)
    logger.info('Updating %s\'s export status after successful EMR status', export_id)
    cmd = subprocess.Popen(bash_cmd, shell=True, stdout=subprocess.PIPE)
    for line in cmd.stdout:
        logger.info(line.strip())

    # Wait until process terminates (without using cmd.wait())
    while cmd.poll() is None:
        time.sleep(0.5)

    if cmd.returncode == 0:
        logger.info('Successfully completed export %s', export_id)
        return True
    else:
        logger.error('Something went wrong with %s', export_id)
        raise AirflowException('Export failed for {}'.format(export_id))

# PythonOperators
@wrap_rollbar
def create_export_definition_op(*args, **kwargs):
    logger.info('Beginning to create export definition')
    xcom_client = kwargs['task_instance']
    conf = kwargs['dag_run'].conf
    export_id = conf['exportId']

    xcom_client.xcom_push(key='export_id',value=export_id)
    return start_export(export_id, xcom_client)

# TASKS
create_export_definition_task = PythonOperator(
    task_id='create_export_definition',
    provide_context=True,
    python_callable=create_export_definition_op,
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

wait_for_status_task.set_upstream(create_export_definition_task)
