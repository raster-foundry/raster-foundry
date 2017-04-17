import datetime
import logging
import os

from airflow.models import DAG
from airflow.operators import PythonOperator, BranchPythonOperator

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
    'start_date': datetime.datetime(2017, 4, 1)
}

dag = DAG(
    dag_id='update_aoi_projects',
    default_args=default_args,
    schedule_interval=None,
    concurrency=os.getenv('AIRFLOW_DAG_CONCURRENCY', 24)
)


def check_whether_update_available(**context):
    # check whether a project needs to update

    conf = context['dag_run'].conf
    project_id = conf.get('project_id')
    logger.info('Checking whether %s has updated scenes available', project_id)

    task_id = 'dont_update_project'
    # this would be replaced by a subprocess call to some jar that determines
    # whether the project needs to be updated
    import random
    update_available = random.random() > 0.5
    if update_available:
        logger.info('AOI project %s has an update available', project_id)
        task_id = 'update_project'

    return task_id


def update_project(**context):
    conf = context['dag_run'].conf
    project_id = conf.get('project_id')
    logger.info('Updating AOI project %s', project_id)


def dont_update_project(**context):
    conf = context['dag_run'].conf
    project_id = conf.get('project_id')
    logger.info('Not updating AOI project %s, no need', project_id)


def mark_project_update_time(**context):
    conf = context['dag_run'].conf
    project_id = conf.get('project_id')
    logger.info('Updating AOI project %s\'s last update time', project_id)


check_operator = BranchPythonOperator(
    task_id='check_project_update_available',
    provide_context=True,
    python_callable=check_whether_update_available,
    dag=dag
)

update_operator = PythonOperator(
    task_id='update_project',
    provide_context=True,
    python_callable=update_project,
    dag=dag
)
update_operator.set_upstream(check_operator)

dont_update_operator = PythonOperator(
    task_id='dont_update_project',
    provide_context=True,
    python_callable=dont_update_project,
    dag=dag
)
dont_update_operator.set_upstream(check_operator)

finish_operator = PythonOperator(
    task_id='mark_project_updated',
    provide_context=True,
    python_callable=mark_project_update_time,
    dag=dag,
    trigger_rule='one_success'
)
finish_operator.set_upstream([update_operator, dont_update_operator])
