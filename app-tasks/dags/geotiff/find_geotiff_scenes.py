from collections import namedtuple
import json
import logging
import os
import boto3
from datetime import datetime

from airflow.models import DAG
from airflow.bin.cli import trigger_dag
from airflow.operators.python_operator import PythonOperator


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

DagArgs = namedtuple('DagArgs', 'dag_id, conf, run_id')

dag = DAG(
    dag_id='find_geotiff_scenes',
    default_args=args,
    schedule_interval=None,
    concurrency=int(os.getenv('AIRFLOW_DAG_CONCURRENCY', 24))
)


def chunkify(lst, n):
    """Helper function to split a list into roughly n chunks

    Args:
        lst (List): list of things to split
        n (Int): number of chunks to split into
    """
    return [lst[i::n] for i in xrange(n)]


def find_geotiffs(*args, **kwargs):
    """Find geotiffs which match the bucket and prefix and kick off imports
    """

    logging.info("Finding geotiff scenes...")

    conf = kwargs['dag_run'].conf

    bucket = conf.get('bucket')
    prefix = conf.get('prefix')

    execution_date = kwargs['execution_date']

    try:
        tilepaths = find_geotiff_scenes(
            bucket, prefix
        )
    except:
        logger.error('encountered error finding tile paths')
        raise


    dag_id = 'import_geotiff_scenes'

    group_max = int(os.getenv('AIRFLOW_CHUNK_SIZE', 32))
    num_groups = group_max if len(tilepaths) >= group_max else len(tilepaths)
    logger.info('Kicking off %s dags to import scene groups', num_groups)

    tilepath_groups = chunkify(tilepaths, num_groups)
    for idx, path_group in enumerate(tilepath_groups):
        slug_path = '_'.join(path_group[0].split('/'))
        run_id = 'geotiff_import_{year}_{month}_{day}_{idx}_{slug}'.format(
            year=execution_date.year, month=execution_date.month, day=execution_date.day,
            idx=idx, slug=slug_path
        )
        logger.info('Kicking off new scene import: %s', run_id)
        conf['tilepaths'] = path_group
        confjson = json.dumps(conf)
        dag_args = DagArgs(dag_id=dag_id, conf=confjson, run_id=run_id)
        trigger_dag(dag_args)

    logger.info('Finished kicking off new Geotiff scene dags')


def find_geotiff_scenes(bucket_name, files_prefix):
    s3 = boto3.resource('s3')
    bucket = s3.Bucket(bucket_name)
    keys = [s3_tif.key for s3_tif in bucket.objects.filter(Prefix=files_prefix)]
    return keys


PythonOperator(
    task_id='find_new_geotiff_scenes',
    provide_context=True,
    python_callable=find_geotiffs,
    dag=dag
)
