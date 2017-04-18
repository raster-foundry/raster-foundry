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
    'start_date': datetime.datetime(2017, 1, 1)
}

dag = DAG(
    dag_id='update_exports',
    default_args=default_args,
    schedule_interval=None,
    concurrency=os.getenv('AIRFLOW_DAG_CONCURRENCY', 24)
)

def update_export(**context):
    # check whether an export needs to update
    conf = context['dag_run'].conf
    export_id = conf.get('export_id')
    logger.info('Checking whether %s is available', export_id)

    task_id = 'dont_update_export'
    # this would be replaced by a subprocess call to some jar that determines
    # whether the export needs to be updated
    import random
    update_available = random.random() > 0.5
    try:
        if update_available:
            logger.info('Export %s has an update available', export_id)
            logger.info('Updating export %s', export_id)
    finally: 
        logger.info('Setting last updated time for export %s', export_id)

PythonOperator(
    task_id='update_export',
    provide_context=True,
    python_callable=update_export,
    sla=datetime.timedelta(minutes=3),
    dag=dag
)
