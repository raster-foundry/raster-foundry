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
    concurrency=1
)


@wrap_rollbar
def find_geotiffs(*args, **kwargs):
    """Find geotiffs which match the bucket and prefix and kick off imports
    """

    logger.info("Finding geotiff scenes...")

    conf = kwargs['dag_run'].conf if kwargs['dag_run'] else {}
    if conf is None:
        conf = {}

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
        # We already tried to check this above but the uploads endpoint doesn't currently
        # support an uploadStatus filter so we have to check again to avoid trying to
        # kick off imports and setting the status a second time
        try:
            if upload.uploadStatus.upper() == 'UPLOADED':
                upload.update_upload_status('Queued')
                conf['upload_id'] = upload_id
                confjson = json.dumps(conf)
                dag_args = DagArgs(dag_id=dag_id, conf=confjson, run_id=run_id)
                trigger_dag(dag_args)
        except:
            upload.update_upload_status('Failed')
            raise

    logger.info('Finished kicking off new uploaded scene dags')


PythonOperator(
    task_id='find_new_geotiff_scenes',
    provide_context=True,
    python_callable=find_geotiffs,
    dag=dag
)
