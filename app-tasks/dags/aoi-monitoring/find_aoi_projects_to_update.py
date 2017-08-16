from collections import namedtuple
import datetime
import logging
import json
import subprocess

from airflow.bin.cli import trigger_dag
from airflow.models import DAG
from airflow.operators.python_operator import PythonOperator

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
    'start_date': datetime.datetime(2017, 4, 7)
}

DagArgs = namedtuple('DagArgs', 'dag_id, conf, run_id, exec_date')

# TODO: update this schedule to something more reasonable/carefully chosen
dag = DAG(
    dag_id='find_aoi_projects_to_update',
    default_args=default_args,
    schedule_interval=datetime.timedelta(hours=6),
    concurrency=1,
    catchup=False
)

#############
# Constants #
#############

FIND_TASK_ID = 'find_aoi_projects_to_update'
KICKOFF_TASK_ID = 'kickoff_aoi_project_update_checks'
UPDATE_DAG_ID = 'update_aoi_projects'

#################################
# Callables for PythonOperators #
#################################

@wrap_rollbar
def find_aoi_projects_to_update():
    """Find AOI projects to check for updates and push their IDs to xcoms"""
    logger.info('Finding AOI projects to check for updates')

    bash_cmd = 'java -cp /opt/raster-foundry/jars/rf-batch.jar com.azavea.rf.batch.Main find_aoi_projects'
    cmd = subprocess.Popen(bash_cmd, shell=True, stdout=subprocess.PIPE)
    projects = ['']
    for line in cmd.stdout:
        logger.info(line.strip())
        if 'ProjectIds:' in line:
            projects = [p.strip() for p in line.replace('ProjectIds:', '').strip().split(',')]

    return {'project_ids': projects}

@wrap_rollbar
def kickoff_aoi_project_update_checks(**context):
    xcom = context['task_instance'].xcom_pull(task_ids=FIND_TASK_ID)
    project_ids = xcom['project_ids']
    logger.info('Found projects to check for updates: %s', project_ids)
    for project_id in project_ids:
        exec_date = datetime.datetime.now()
        run_id = 'update_aoi_{}_{}'.format(project_id, exec_date.isoformat())
        conf = json.dumps({'project_id': project_id})
        dag_args = DagArgs(dag_id=UPDATE_DAG_ID, conf=conf, run_id=run_id, exec_date=exec_date)
        trigger_dag(dag_args)

find_operator = PythonOperator(
    task_id=FIND_TASK_ID,
    provide_context=False,
    python_callable=find_aoi_projects_to_update,
    on_failure_callback=failure_callback,
    dag=dag
)

kickoff_operator = PythonOperator(
    task_id=KICKOFF_TASK_ID,
    provide_context=True,
    python_callable=kickoff_aoi_project_update_checks,
    on_failure_callback=failure_callback,
    dag=dag
)
kickoff_operator.set_upstream(find_operator)
