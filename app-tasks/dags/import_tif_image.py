from airflow.operators.python_operator import PythonOperator
from airflow.models import DAG
from datetime import datetime, timedelta

from rf.uploads.geotiff import (
    create_thumbnail,
    extract_metadata,
    extract_footprint
)


import logging

logger = logging.getLogger('rf')
ch = logging.StreamHandler()
ch.setLevel(logging.DEBUG)
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
ch.setFormatter(formatter)
logger.addHandler(ch)

seven_days_ago = datetime.combine(
        datetime.today() - timedelta(7), datetime.min.time())


args = {
    'owner': 'airflow',
    'start_date': seven_days_ago,
}


dag = DAG(
    dag_id='import_tif_image',
    default_args=args,
    schedule_interval=None
)


def create_thumbnails(*args, **kwargs):
    tif_path = kwargs['dag_run'].conf.get('tif_path', 'example.tif')
    """This is a function that will run within the DAG execution"""
    create_thumbnail(tif_path)
    return kwargs


def extract_tif_metadata(*args, **kwargs):
    tif_path = kwargs['dag_run'].conf.get('tif_path', 'example.tif')
    extract_metadata(tif_path)
    return kwargs


def extract_polygon(*args, **kwargs):
    tif_path = kwargs['dag_run'].conf.get('tif_path', 'example.tif')
    extract_footprint(tif_path)
    return kwargs


PythonOperator(
    task_id='create_thumbnails',
    provide_context=True,    
    python_callable=create_thumbnails,
    dag=dag
)

PythonOperator(
    task_id='extract_tif_metadata',
    provide_context=True,    
    python_callable=extract_tif_metadata,
    dag=dag
)

PythonOperator(
    task_id='extract_polygons',
    provide_context=True,    
    python_callable=extract_polygon,
    dag=dag
)
