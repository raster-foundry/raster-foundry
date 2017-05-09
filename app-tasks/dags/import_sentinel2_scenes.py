import os

from airflow.operators.bash_operator import BashOperator
from airflow.models import DAG
from datetime import datetime, timedelta

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
    concurrency=int(os.getenv('AIRFLOW_DAG_CONCURRENCY', 24))
)

bash_cmd = "java -cp /opt/raster-foundry/jars/rf-batch.jar com.azavea.rf.batch.Main import_sentinel2 {{ yesterday_ds }}"

sentinel2_importer = BashOperator(
    task_id='import_sentinel2',
    provide_context=True,
    bash_command=bash_cmd,
    dag=dag
)
