from collections import namedtuple
from datetime import datetime, timedelta
import json
import logging
import os

from airflow.models import DAG
from airflow.bin.cli import trigger_dag
from airflow.operators.python_operator import PythonOperator

from rf.utils.exception_reporting import wrap_rollbar
from rf.models import Upload

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
    schedule_interval=timedelta(minutes=2),
    concurrency=int(os.getenv('AIRFLOW_DAG_CONCURRENCY', 24))
)


def chunkify(lst, n):
    """Helper function to split a list into roughly n chunks

    Args:
        lst (List): list of things to split
        n (Int): number of chunks to split into
    """
    return [lst[i::n] for i in xrange(n)]


@wrap_rollbar
def find_geotiffs(*args, **kwargs):
    """Find geotiffs which match the bucket and prefix and kick off imports
    """

    logger.info("Finding geotiff scenes...")

    conf = kwargs['dag_run'].conf if kwargs['dag_run'] else {}

    try:
        upload_ids = Upload.get_importable_uploads()
    except:
        logger.error('encountered error finding tile paths')
        raise


    dag_id = 'import_geotiff_scenes'

    for upload_id in upload_ids:
        run_id = 'geotiff_import_{upload_id}'.format(
            upload_id=upload_id
        )
        logger.info('Kicking off new scene import: %s', run_id)
        upload = Upload.from_id(upload_id)
        upload.update_upload_status('Queued')
        conf['upload_id'] = upload_id
        confjson = json.dumps(conf)
        dag_args = DagArgs(dag_id=dag_id, conf=confjson, run_id=run_id)
        trigger_dag(dag_args)

    logger.info('Finished kicking off new uploaded scene dags')


PythonOperator(
    task_id='find_new_geotiff_scenes',
    provide_context=True,
    python_callable=find_geotiffs,
    dag=dag
)
