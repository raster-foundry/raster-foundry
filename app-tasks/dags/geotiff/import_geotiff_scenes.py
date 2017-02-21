import logging
import os
import tempfile
import boto3
from datetime import datetime

from airflow.operators.python_operator import PythonOperator

from airflow.models import DAG

from rf.uploads.geotiff import GeoTiffS3SceneFactory
from rf.uploads.geotiff.io import s3_url
from rf.uploads.geotiff.create_thumbnails import create_thumbnails
from rf.utils.io import Visibility

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


def import_geotiffs(*args, **kwargs):
    """Find geotiffs which match the bucket and prefix and kick off imports
    """

    logging.info("Finding geotiff scenes...")
    conf = kwargs['dag_run'].conf

    tilepaths = conf.get('tilepaths')
    organization = conf.get('organization')
    datasource = conf.get('datasource')
    capture_date = conf.get('capture_date')
    bucket_name = conf.get('bucket')

    factory = GeoTiffS3SceneFactory(
        organization, Visibility.PRIVATE, datasource,
        capture_date, bucket_name, ''
    )

    s3 = boto3.resource('s3')
    bucket = s3.Bucket(bucket_name)

    for path in tilepaths:
        local_tif = tempfile.NamedTemporaryFile(delete=False)
        try:
            bucket.download_file(path, local_tif.name)
            # We need to override the autodetected filename because we're loading into temp
            # files which don't preserve the file name that is on S3.
            filename = os.path.basename(path)
            scene = factory.create_geotiff_scene(local_tif.name, os.path.splitext(filename)[0])
            image = factory.create_geotiff_image(
                local_tif.name, s3_url(bucket.name, path),
                scene.id, filename
            )

            scene.thumbnails = create_thumbnails(local_tif.name, scene.id, organization)
            scene.images = [image]
            scene.create()
        finally:
            os.remove(local_tif.name)

    logger.info('Finished importing scenes')


geotiff_importer = PythonOperator(
    task_id='import_geotiffs',
    python_callable=import_geotiffs,
    provide_context=True,
    dag=dag
)
