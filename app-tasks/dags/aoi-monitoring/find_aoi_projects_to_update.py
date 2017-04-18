from collections import namedtuple
import datetime
import logging
import json
import subprocess

from airflow.bin.cli import trigger_dag
from airflow.models import DAG
from airflow.operators.python_operator import PythonOperator

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
    'start_date': datetime.datetime(2017, 4, 7)
}

DagArgs = namedtuple('DagArgs', 'dag_id, conf, run_id')

# TODO: update this schedule to something more reasonable/carefully chosen
dag = DAG(
    dag_id='find_aoi_projects_to_update',
    default_args=default_args,
    schedule_interval=None,
    concurrency=1
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
    # This is check_output to mock shelling out to some scala command
    # and returning a sequence of UUIDs
    projects = subprocess.check_output([
        'echo', 'some-project, ids, here, maybe, many, of-them'
    ])
    return {'project_ids': projects.strip().split(', ')}


@wrap_rollbar
def kickoff_aoi_project_update_checks(**context):
    xcom = context['task_instance'].xcom_pull(task_ids=FIND_TASK_ID)
    project_ids = xcom['project_ids']
    logger.info('Found projects to check for updates: %s', project_ids)
    execution_date = context['execution_date']
    # TODO: remove this
    # so we can keep kicking off jobs with the same bogus ids from the 
    def add_random_text(s):
        """Add random text to a string s"""
        import random
        from string import ascii_lowercase
        letters = [random.choice(ascii_lowercase) for _ in xrange(25)]
        return s + ''.join(letters)

    for project_id in project_ids:
        # Run ID goes out to seconds because users may have really high frequency cadences
        # Maybe we shouldn't allow cadences below a certain value?
        run_id = (
            'aoi_project_update_{project_id}_{year}_{month}_{day}_{hour}_{minute}_{second}'
        ).format(
            project_id=project_id,
            year=execution_date.year,
            month=execution_date.month,
            day=execution_date.day,
            hour=execution_date.hour,
            minute=execution_date.minute,
            second=execution_date.second
        )
        run_id = add_random_text(run_id)
        conf = json.dumps({'project_id': project_id})
        dag_args = DagArgs(dag_id=UPDATE_DAG_ID, conf=conf, run_id=run_id)
        trigger_dag(dag_args)


find_operator = PythonOperator(
    task_id=FIND_TASK_ID,
    provide_context=False,
    python_callable=find_aoi_projects_to_update,
    dag=dag
)

kickoff_operator = PythonOperator(
    task_id=KICKOFF_TASK_ID,
    provide_context=True,
    python_callable=kickoff_aoi_project_update_checks,
    dag=dag
)
kickoff_operator.set_upstream(find_operator)
