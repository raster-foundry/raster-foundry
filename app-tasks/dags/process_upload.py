import logging
import os
from datetime import datetime

from airflow.operators.python_operator import PythonOperator
from airflow.models import DAG

from planet import api

from rf.models import Upload
from rf.uploads.geotiff.factories import GeoTiffS3SceneFactory
from rf.uploads.planet.factories import PlanetSceneFactory
from rf.utils.io import get_session
from rf.utils.exception_reporting import wrap_rollbar

from utils import failure_callback


# Logging Setup
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
ch = logging.StreamHandler()

ch.setLevel(logging.INFO)
ch.setFormatter(formatter)

logging.getLogger('rf').addHandler(ch)

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

    logger.info('Processing uploads...')
    conf = kwargs['dag_run'].conf

    logger.info('Getting upload')
    upload_id = conf.get('uploadId')
    upload = Upload.from_id(upload_id)
    logger.info('Updating upload status')
    upload.update_upload_status('Processing')

    logger.info('Processing upload (%s) for user %s with files %s',
                upload.id, upload.owner, upload.files)

    try:
        if upload.uploadType.lower() in ['local', 's3']:
            logger.info('Processing a geotiff upload')
            factory = GeoTiffS3SceneFactory(upload)
        elif upload.uploadType.lower() == 'planet':
            logger.info('Processing a planet upload. This might take a while...')
            factory = PlanetSceneFactory(
                upload.files,
                upload.datasource,
                upload.organizationId,
                upload.id,
                upload.projectId,
                upload.visibility,
                [],
                upload.owner,
                api.ClientV1(upload.metadata.get('planetKey'))
            )
        else:
            raise Exception('upload type didn\'t make any sense')
        scenes = factory.generate_scenes()
        logger.info('Creating scene objects for upload %s, preparing to POST to API', upload.id)

        created_scenes = [scene.create() for scene in scenes]
        logger.info('Successfully created %s scenes (%s)', len(created_scenes), [s.id for s in created_scenes])

        if upload.projectId:
            logger.info('Upload specified a project. Linking scenes to project %s', upload.projectId)
            scene_ids = [scene.id for scene in created_scenes]
            batch_scene_to_project_url = '{HOST}/api/projects/{PROJECT}/scenes'.format(HOST=HOST, PROJECT=upload.projectId)
            session = get_session()
            response = session.post(batch_scene_to_project_url, json=scene_ids)
            response.raise_for_status()
        upload.update_upload_status('Complete')
        logger.info('Finished importing scenes for upload (%s) for user %s with files %s',
                     upload.id, upload.owner, upload.files)
    except:
        logger.error('Failed to process upload (%s) for user %s with files %s',
                     upload.id, upload.owner, upload.files)
        upload.update_upload_status('Failed')
        raise


process_upload_op = PythonOperator(
    task_id='process_upload',
    python_callable=process_upload,
    provide_context=True,
    on_failure_callback=failure_callback,
    dag=dag
)
