"""Task for scheduled finding new Landsat 8 scenes to ingest"""

from datetime import datetime
import os

from airflow.operators.bash_operator import BashOperator
from airflow.models import DAG

from utils import failure_callback


schedule = None if os.getenv('ENVIRONMENT') == 'development' else '@daily'
start_date = datetime(2016, 11, 6)
args = {
    'owner': 'raster-foundry',
    'start_date': start_date
}

dag = DAG(
    dag_id='find_landsat8_scenes',
    default_args=args,
    schedule_interval=schedule,
    concurrency=int(os.getenv('AIRFLOW_DAG_CONCURRENCY', 24)),
    catchup=False
)

bash_cmd = "java -cp /opt/raster-foundry/jars/rf-batch.jar com.azavea.rf.batch.Main import_landsat8 {{ yesterday_ds }}"

landsat8_finder = BashOperator(
    task_id='import_new_landsat8_scenes',
    bash_command=bash_cmd,
    on_failure_callback=failure_callback,
    dag=dag
)
