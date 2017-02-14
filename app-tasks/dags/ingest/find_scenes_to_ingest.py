"""DAG to check if there are scenes to be ingested.

Queries the API for scenes that have a status 'TOBEINGESTED'
and kicks off an ingest DAG for each scene.
"""

from collections import namedtuple
import datetime
import json
import logging
import os
from time import time

from airflow.operators.python_operator import PythonOperator
from airflow.models import DAG
from airflow.bin.cli import trigger_dag

from rf.utils.io import get_jwt, get_session, IngestStatus

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


http_connection_id = 'raster-foundry'
base_params = {'ingestStatus': IngestStatus.TOBEINGESTED}
headers = {'Authorization': 'Bearer {}'.format(get_jwt())}
host = os.getenv('RF_HOST')

DagArgs = namedtuple('DagArgs', 'dag_id, conf, run_id')


def get_uningested_scenes():
    """Requests uningested scenes from API and returns list

    Returns:
        List[Dict]
    """
    params = base_params.copy()
    params['page'] = 0
    session = get_session()

    scene_url = '{host}/api/scenes/'.format(host=host)
    scene_response = session.get(scene_url, params=params).json()
    scenes = scene_response['results']
    scenes_remain = scene_response['hasNext']
    while scenes_remain:
        params['page'] += 1
        logger.debug('Requesting page %s for scenes', params['page'])
        scene_response = session.get(scene_url, params=params).json()
        scenes += scene_response['results']
        scenes_remain = scene_response['hasNext']
    return scenes


def check_for_scenes_to_ingest():
    """Requests uningested scenes, kicks off ingest DAG for each scene

    Notes
      At some point this should batch scene ingests together, but for now
      they are kept separate for debugging and because the ingests themselves
      do not parallelize well
    """
    logger.info("Requesting uningested scenes...")
    scenes = get_uningested_scenes()

    dag_id = 'ingest_project_scenes'

    if len(scenes) == 0:
        return 'No scenes to ingest'

    logger.info('Kicking off ingests for %s scenes', len(scenes))
    for scene in scenes:
        run_id = 'scene_ingest_{}_{}'.format(scene['id'], time())
        logger.info('Kicking off new scene ingest: %s', run_id)
        conf = json.dumps({'scene': scene})
        dag_args = DagArgs(dag_id=dag_id, conf=conf, run_id=run_id)
        trigger_dag(dag_args)
    return "Finished kicking off ingests"


dag = DAG(
    dag_id='find_scenes_to_ingest',
    default_args=default_args,
    concurrency=int(os.getenv('AIRFLOW_DAG_CONCURRENCY', 24)),
    schedule_interval=datetime.timedelta(minutes=2),
    max_active_runs=1
)


PythonOperator(
    task_id='check_for_scenes_to_ingest',
    python_callable=check_for_scenes_to_ingest,
    dag=dag
)
