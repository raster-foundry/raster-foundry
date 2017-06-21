import logging
import os
from datetime import datetime

from airflow.operators.python_operator import PythonOperator

from airflow.models import DAG

from rf.models import Upload
from rf.uploads.geotiff.factories import GeoTiffS3SceneFactory
from rf.utils.io import get_session
from rf.utils.exception_reporting import wrap_rollbar

# Logging Setup
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
ch = logging.StreamHandler()

ch.setLevel(logging.INFO)
ch.setFormatter(formatter)

logging.getLogger('rf').addHandler(ch)
logging.getLogger().addHandler(ch)

logger = logging.getLogger(__name__)


args = {
    'start_date': datetime(2017, 2, 21)
}

dag = DAG(
    dag_id='process_upload',
    default_args=args,
    schedule_interval=None,
    concurrency=int(os.getenv('AIRFLOW_DAG_CONCURRENCY', 24))
)

HOST = os.getenv('RF_HOST')

@wrap_rollbar
def process_upload(*args, **kwargs):
    """Import scenes for a given upload"""

    logger.info('Processing geotiff uploads...')
    conf = kwargs['dag_run'].conf

    upload_id = conf.get('uploadId')
    upload = Upload.from_id(upload_id)
    upload.update_upload_status('Processing')

    try:
        factory = GeoTiffS3SceneFactory(upload)
        scenes = factory.generate_scenes()
        created_scenes = [scene.create() for scene in scenes]

        if upload.projectId:
            logger.info("Upload specified a project. Linking scenes to project.")
            scene_ids = [scene.id for scene in created_scenes]
            batch_scene_to_project_url = '{HOST}/api/projects/{PROJECT}/scenes'.format(HOST=HOST, PROJECT=upload.projectId)
            session = get_session()
            response = session.post(batch_scene_to_project_url, json=scene_ids)
            response.raise_for_status()
        upload.update_upload_status('Complete')
        logger.info('Finished importing scenes')
    except:
        upload.update_upload_status('Failed')
        raise


process_upload_op = PythonOperator(
    task_id='process_upload',
    python_callable=process_upload,
    provide_context=True,
    dag=dag
)
