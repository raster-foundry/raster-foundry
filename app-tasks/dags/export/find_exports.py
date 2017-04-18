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
    'start_date': datetime.datetime(2017, 1, 1)
}

DagArgs = namedtuple('DagArgs', 'dag_id, conf, run_id')

dag = DAG(
    dag_id='find_exports',
    default_args=default_args,
    schedule_interval=datetime.timedelta(minutes=1),
    concurrency=1
)

#############
# Constants #
#############

FIND_TASK_ID = 'find_exports'
KICKOFF_TASK_ID = 'kickoff_export_checks'
UPDATE_DAG_ID = 'update_exports'

#################################
# Callables for PythonOperators #
#################################

@wrap_rollbar
def find_exports():
    """Find exports to check for updates and push their IDs to xcoms"""
    logger.info('Finding AOI projects to check for updates')
    # This is check_output to mock shelling out to some scala command
    # and returning a sequence of UUIDs
    exports = subprocess.check_output([
        'echo', 'some nonsense'
    ])
    return {'export_ids': exports.strip().split(', ')}


@wrap_rollbar
def kickoff_export_checks(**context):
    xcom = context['task_instance'].xcom_pull(task_ids=FIND_TASK_ID)
    export_ids = xcom['export_ids']
    logger.info('Found exports to check for updates: %s', export_ids)
    execution_date = context['execution_date']
    # TODO: remove this
    # so we can keep kicking off jobs with the same bogus ids from the 
    def add_random_text(s):
        """Add random text to a string s"""
        import random
        from string import ascii_lowercase
        letters = [random.choice(ascii_lowercase) for _ in xrange(25)]
        return s + ''.join(letters)

    for export_id in export_ids:
        # Run ID goes out to seconds because users may have really high frequency cadences
        # Maybe we shouldn't allow cadences below a certain value?
        run_id = (
            'export_update_{export_id}_{year}_{month}_{day}_{hour}_{minute}_{second}'
        ).format(
            export_id=export_id,
            year=execution_date.year,
            month=execution_date.month,
            day=execution_date.day,
            hour=execution_date.hour,
            minute=execution_date.minute,
            second=execution_date.second
        )
        run_id = add_random_text(run_id)
        conf = json.dumps({'export_id': export_id})
        dag_args = DagArgs(dag_id=UPDATE_DAG_ID, conf=conf, run_id=run_id)
        trigger_dag(dag_args)


find_operator = PythonOperator(
    task_id=FIND_TASK_ID,
    provide_context=False,
    python_callable=find_exports,
    dag=dag
)

kickoff_operator = PythonOperator(
    task_id=KICKOFF_TASK_ID,
    provide_context=True,
    python_callable=kickoff_export_checks,
    dag=dag
)
kickoff_operator.set_upstream(find_operator)
