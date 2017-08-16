import os

from airflow.operators.bash_operator import BashOperator
from airflow.models import DAG
from datetime import datetime, timedelta

from utils import failure_callback


seven_days_ago = datetime.combine(
    datetime.today() - timedelta(7), datetime.min.time())

args = {
    'owner': 'raster-foundry',
    'start_date': seven_days_ago
}

dag = DAG(
    dag_id='import_sentinel2_scenes',
    default_args=args,
    schedule_interval=None,
    concurrency=int(os.getenv('AIRFLOW_DAG_CONCURRENCY', 24)),
    catchup=False
)

bash_cmd = "java -cp /opt/raster-foundry/jars/rf-batch.jar com.azavea.rf.batch.Main import_sentinel2 {{ yesterday_ds }}"

sentinel2_importer = BashOperator(
    task_id='import_sentinel2',
    bash_command=bash_cmd,
    on_failure_callback=failure_callback,
    dag=dag
)
