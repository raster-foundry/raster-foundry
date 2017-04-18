import os
from collections import namedtuple
import datetime
import logging
import json
import boto3
from time import time

from airflow.bin.cli import trigger_dag
from airflow.models import DAG
from airflow.operators.python_operator import PythonOperator

from rf.utils.io import get_session
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

base_params = {'exportStatus': 'NOTEXPORTED'}

DagArgs = namedtuple('DagArgs', 'dag_id, conf, run_id')


HOST = os.getenv('RF_HOST')
API_PATH = '/api/exports/'
#############
# Constants #
#############

FIND_TASK_ID = 'find_exports'
#KICKOFF_TASK_ID = 'kickoff_export_checks'
UPDATE_DAG_ID = 'do_export'

# UTILS
def update_status(export, status):
    url = '{HOST}{API_PATH}{id}/'.format(HOST=HOST, API_PATH=API_PATH, id=export['id'])
    session = get_session()
    response = session.get(url)
    obj = response.json()
    obj['exportStatus'] = status
    json_d = json.dumps(obj)
    update_response = session.put(url, headers={'Content-Type':'application/json'}, data=json_d)
    try:
        update_response.raise_for_status()
    except:
        logger.exception('Unable to update: %s', update_response.text)
        raise
    return update_response

#################################
# Callables for PythonOperators #
#################################
@wrap_rollbar
def get_exports():
    session = get_session()
    host = HOST
    params = base_params.copy()
    params['page'] = 0 
    exports_url = '{host}{api}'.format(host=host, api=API_PATH)
    response = session.get(exports_url, params=params).json()
    queued_exports = response['results']
    remaining_exports = response['hasNext']

    while remaining_exports:
        params['page'] += 1
        logger.debug('Requesting page %s of queued exports', params['page'])
        resp = session.get(exports_url, params=params).json()
        queued_exports = resp['results']
        remaining_exports = resp['hasNext']
    return queued_exports

@wrap_rollbar
def find_exports():
    """Find exports to check for updates and push their IDs to xcoms"""
    logger.info('Finding queued exports')
    exports = get_exports()
    dag_id = 'do_export'

    if len(exports) == 0:
        return 'No queued exports'

    logger.info('Initiating export processes for %s exports', len(exports))
    for export in exports:
        # update export status so it isn't picked up again
        update_status(export, 'TOBEEXPORTED')


        run_id = 'export_{}_{}'.format(export['id'], time())
        logger.info('Starting export: %s', run_id)
        conf = json.dumps({'exportId': export['id']})
        dag_args = DagArgs(dag_id=dag_id, conf=conf, run_id=run_id)
        trigger_dag(dag_args)
    return 'Finished kicking off exports'

dag = DAG(
    dag_id='find_exports',
    default_args=default_args,
    schedule_interval=datetime.timedelta(seconds=10),
    concurrency=os.getenv('AIRFLOW_DAG_CONCURRENCY', 24)
)

PythonOperator(
    task_id=FIND_TASK_ID,
    python_callable=find_exports,
    dag=dag
)
