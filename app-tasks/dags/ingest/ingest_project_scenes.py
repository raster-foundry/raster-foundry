import datetime
import logging
import os

from airflow.operators.python_operator import PythonOperator
from airflow.models import DAG

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
    concurrency=int(os.getenv('AIRFLOW_DAG_CONCURRENCY', 24))
)


################################
# Utility functions            #
################################
def get_uningested_project_scenes():
    logger.info('Querying for scenes which need to be ingested...')
    return []


def create_ingest_definition(scenes_to_ingest):
    logger.info('Creating ingest definition...')
    return str(scenes_to_ingest)


def store_ingest_definition(ingest_definition_str):
    # Possibly use Airflow's builtin S3Hook here
    logger.info('Uploading ingest definition to S3')


################################
# Callables for PythonOperators#
################################
def create_ingest_definition_op():
    logger.info('Beginning to create ingest definition...')
    scenes_to_ingest = get_uningested_project_scenes()
    ingest_definition = create_ingest_definition(scenes_to_ingest)
    # TODO: Depending on whether the ingest definition provides us with enough
    # info to update the Scenes' ingested status when the ingest task succeeds
    # / fails, we may also need to create and upload a list of Scene IDs at
    # this stage.
    logger.info('Uploading ingest definition...')
    store_ingest_definition(ingest_definition)
    logger.info('Finished creating ingest definition.')


def launch_spark_ingest_job_op():
    logger.info('Launching Spark ingest job...')
    logger.info('Finished launching Spark ingest job.')


def set_ingest_status_success_op():
    logger.info("Setting scenes' ingested status to success...")
    logger.info("Finished setting scenes' ingested status.")


def set_ingest_status_failure_op():
    logger.info("Setting scenes' ingested status to failure...")
    logger.info("Finished setting scenes' ingested status.")

################################
# Tasks                        #
################################
create_ingest_definition_task = PythonOperator(
    task_id='create_ingest_definition',
    python_callable=create_ingest_definition_op,
    dag=dag
)

launch_spark_ingest_task = PythonOperator(
    task_id='launch_spark_ingest',
    python_callable=launch_spark_ingest_job_op,
    dag=dag
)

set_ingest_status_success_task = PythonOperator(
    task_id='set_ingest_status_success',
    python_callable=set_ingest_status_success_op,
    trigger_rule='all_success',
    dag=dag
)

set_ingest_status_failure_task = PythonOperator(
    task_id='set_ingest_status_failure',
    python_callable=set_ingest_status_failure_op,
    trigger_rule='all_failed',
    dag=dag
)

################################
# DAG Structure Specification  #
################################
set_ingest_status_success_task.set_upstream(launch_spark_ingest_task)
set_ingest_status_failure_task.set_upstream(launch_spark_ingest_task)
launch_spark_ingest_task.set_upstream(create_ingest_definition_task)
