import logging
import os
import tempfile
import boto3
from datetime import datetime

from airflow.operators.python_operator import PythonOperator

from airflow.models import DAG

from rf.models import Upload
from rf.uploads.geotiff.factories import GeoTiffS3SceneFactory
from rf.uploads.geotiff.io import s3_url
from rf.uploads.geotiff.create_thumbnails import create_thumbnails
from rf.utils.io import Visibility
from rf.utils.exception_reporting import wrap_rollbar


rf_logger = logging.getLogger('rf')
ch = logging.StreamHandler()
ch.setLevel(logging.INFO)
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
ch.setFormatter(formatter)
rf_logger.addHandler(ch)

logger = logging.getLogger(__name__)

args = {
    'start_date': datetime(2017, 2, 21)
}

dag = DAG(
    dag_id='import_geotiff_scenes',
    default_args=args,
    schedule_interval=None,
    concurrency=int(os.getenv('AIRFLOW_DAG_CONCURRENCY', 24))
)


@wrap_rollbar
def import_geotiffs(*args, **kwargs):
    """Find geotiffs which match the bucket and prefix and kick off imports"""

    logging.info('Processing geotiff uploads...')
    conf = kwargs['dag_run'].conf

    upload_id = conf.get('upload_id')
    upload = Upload.from_id(upload_id)
    upload.update_upload_status('Processing')

    try:
        factory = GeoTiffS3SceneFactory(upload)
        scenes = factory.generate_scenes()
        for scene in scenes:
            scene.create()
        upload.update_upload_status('Complete')
        logger.info('Finished importing scenes')
    except:
        upload.update_upload_status('Failed')
        raise


geotiff_importer = PythonOperator(
    task_id='import_geotiffs',
    python_callable=import_geotiffs,
    provide_context=True,
    dag=dag
)
