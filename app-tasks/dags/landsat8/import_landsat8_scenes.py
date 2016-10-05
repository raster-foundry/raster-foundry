"""Task for scheduled finding new Landsat 8 scenes to ingest"""

from datetime import datetime
import logging

from airflow.operators.python_operator import PythonOperator
from airflow.models import DAG

from rf.uploads.landsat8 import find_landsat8_scenes, create_landsat8_scenes

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
    dag_id='find_landsat8_scenes',
    default_args=args,
    schedule_interval=None
)


def import_landsat8_scenes(*args, **kwargs):
    """Find new Landsat 8 scenes and kick off imports

    Uses the execution date from the Airflow context to determine what day
    to check for imports.
    """
    execution_date = kwargs['execution_date']
    logging.info('Finding Landsat 8 scenes for date: %s', execution_date)

    csv_rows = find_landsat8_scenes(execution_date.year, execution_date.month,
                                    execution_date.day)
    if not csv_rows:
        raise ValueError('No rows found to import for %s' % execution_date)
    logger.info('Importing %d csv rows...', len(csv_rows))
    for row in csv_rows:
        scene_id = row['sceneID']
        logger.info('Importing scenes from row %s...', scene_id)
        scenes = create_landsat8_scenes(row)
        for scene in scenes:
            scene.create()
        logger.info('Finished importing scenes for row %s', scene_id)


landsat8_finder = PythonOperator(
    task_id='import_new_landsat8_scenes'.format(
        year=start_date.year, month=start_date.month, day=start_date.day
    ),
    provide_context=True,
    python_callable=import_landsat8_scenes,
    dag=dag
)
