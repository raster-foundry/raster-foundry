import datetime
import logging
import os
import subprocess

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


default_args = {
    'owner': 'raster-foundry',
    'start_date': datetime.datetime(2017, 4, 1)
}

dag = DAG(
    dag_id='update_aoi_projects',
    default_args=default_args,
    schedule_interval=None,
    concurrency=os.getenv('AIRFLOW_DAG_CONCURRENCY', 24)
)

@wrap_rollbar
def start_update(project_id):
    bash_cmd = 'java -cp /opt/raster-foundry/jars/rf-batch.jar com.azavea.rf.batch.Main update_aoi_project {0}'.format(project_id)

    exit_code = subprocess.call([bash_cmd], shell=True)
    logger.info('Checking whether %s has updated scenes available', project_id)
    is_success = exit_code == 0

    if is_success:
        logger.info('Successfully completed project %s update', project_id)
    else:
        raise AirflowException('Update of project %s failed', project_id)

    return is_success

@wrap_rollbar
def update_aoi_project(**context):
    # check whether a project needs to update
    conf = context['dag_run'].conf
    project_id = conf.get('project_id')
    start_update(project_id)

PythonOperator(
    task_id='update_aoi_project',
    provide_context=True,
    python_callable=update_aoi_project,
    execution_timeout=datetime.timedelta(seconds=60),
    on_failure_callback=failure_callback,
    dag=dag
)
