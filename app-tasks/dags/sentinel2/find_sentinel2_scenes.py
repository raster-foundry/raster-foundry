from collections import namedtuple
import json
import logging

from airflow.bin.cli import trigger_dag
from airflow.operators.python_operator import PythonOperator
from airflow.models import DAG
from datetime import datetime

from rf.uploads.sentinel2 import find_sentinel2_scenes

rf_logger = logging.getLogger('rf')
ch = logging.StreamHandler()
ch.setLevel(logging.INFO)
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
ch.setFormatter(formatter)
rf_logger.addHandler(ch)

logger = logging.getLogger(__name__)


start_date = datetime(2016, 9, 25)


args = {
    'owner': 'raster-foundry',
    'start_date': start_date
}


dag = DAG(
    dag_id='find_sentinel2_scenes',
    default_args=args,
    schedule_interval=None
)


DagArgs = namedtuple('DagArgs', 'dag_id, conf, run_id')


def chunkify(lst, n):
    """Helper function to split a list into roughly n chunks

    Args:
        lst (List): list of things to split
        n (Int): number of chunks to split into
    """
    return [lst[i::n] for i in xrange(n)]


def find_new_sentinel2_scenes(*args, **kwargs):
    """Find new Sentinel 2 scenes and kick off imports

    Uses the execution date to determine what day to check for imports
    """

    logging.info("Finding Sentinel-2 scenes...")
    execution_date = kwargs['execution_date']
    tilepaths = find_sentinel2_scenes(
        execution_date.year, execution_date.month, execution_date.day
    )

    dag_id = 'import_sentinel2_scenes'

    # Split into groups for more efficient jobs
    num_groups = 32 if len(tilepaths) >= 32 else len(tilepaths)
    logger.info('Kicking off %s dags to import scene groups', num_groups)
    tilepath_groups = chunkify(tilepaths, num_groups)
    for idx, path_group in enumerate(tilepath_groups):
        slug_path = '_'.join(path_group[0].split('/'))
        run_id = 'sentinel2_import_{year}_{month}_{day}_{idx}_{slug}'.format(
            year=execution_date.year, month=execution_date.month, day=execution_date.day,
            idx=idx, slug=slug_path
        )
        logger.info('Kicking off new scene import: %s', run_id)
        conf = json.dumps({'tilepaths': path_group})
        dag_args = DagArgs(dag_id=dag_id, conf=conf, run_id=run_id)
        trigger_dag(dag_args)
    return "Finished kicking off new Sentinel-2 dags"


PythonOperator(
    task_id='find_new_sentinel2_scenes',
    provide_context=True,
    python_callable=find_new_sentinel2_scenes,
    dag=dag
)
