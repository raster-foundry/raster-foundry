from __future__ import print_function
import datetime
import logging
import os

import subprocess

from airflow.models import DAG
from airflow.operators import PythonOperator
from airflow.exceptions import AirflowException

from rf.utils.exception_reporting import wrap_rollbar


rf_logger = logging.getLogger('rf')
ch = logging.StreamHandler()
ch.setLevel(logging.INFO)
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
ch.setFormatter(formatter)
rf_logger.addHandler(ch)

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
def start_export(export_id):
    bash_cmd = 'java -cp /opt/raster-foundry/jars/rf-batch.jar com.azavea.rf.batch.Main create_export_def {0}'.format(export_id)

    subprocess.call([bash_cmd], shell=True)
    logger.info('Launched export creation process, watching for updates...')
    is_success = wait_for_success()

    return is_success

def wait_for_success(response=None):
    # TODO: this is spurious until a means of determining success or failure is used
    successful = True
    if successful:
        logger.info('Successfully completed export')
        return True
    else:
        raise AirflowException('Export failed')

# PythonOperators

@wrap_rollbar
def create_export_definition_op(*args, **kwargs):
    logger.info('Beginning to create export definition')
    xcom_client = kwargs['task_instance']
    conf = kwargs['dag_run'].conf
    export_id = conf['exportId']


    xcom_client.xcom_push(key='export_def_id',value=export_id)
    start_export(export_id)

# TASKS
create_export_definition_task = PythonOperator(
    task_id='create_export_definition',
    provide_context=True,
    python_callable=create_export_definition_op,
    dag=dag
)
