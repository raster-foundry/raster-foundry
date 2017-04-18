import datetime
import logging
import os

from airflow.models import DAG
from airflow.operators import PythonOperator

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


def update_aoi_project(**context):
    # check whether a project needs to update
    conf = context['dag_run'].conf
    project_id = conf.get('project_id')
    logger.info('Checking whether %s has updated scenes available', project_id)

    task_id = 'dont_update_project'
    # this would be replaced by a subprocess call to some jar that determines
    # whether the project needs to be updated
    import random
    update_available = random.random() > 0.5
    try:
        if update_available:
            logger.info('AOI project %s has an update available', project_id)
            logger.info('Updating AOI project %s', project_id)
    finally: 
        logger.info('Setting last updated time for project %s', project_id)

PythonOperator(
    task_id='update_aoi_project',
    provide_context=True,
    python_callable=update_aoi_project,
    execution_timeout=datetime.timedelta(seconds=60),
    dag=dag
)
