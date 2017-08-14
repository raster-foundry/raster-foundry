"""API endpoint to trigger a dag run"""
import json
from collections import namedtuple
from airflow import settings
from airflow.models import DagModel
from airflow.plugins_manager import AirflowPlugin
from airflow.www.app import csrf
from datetime import datetime
from flask import Blueprint, request, jsonify, Response
import httplib


from airflow.bin.cli import trigger_dag

# Flask Blueprint for Triggering a Dag
APIBlueprint = Blueprint('api', __name__, url_prefix='/api')

DagArgs = namedtuple('DagArgs', 'dag_id, conf, run_id, exec_date')

DAG = namedtuple('DAG', 'dag_id, id_field')
DAGs = {
    'process_upload': DAG('process_upload', 'uploadId'),
    'export_project': DAG('export_project', 'exportId'),
    'ingest_scene': DAG('ingest_scene', 'sceneId')
}

@APIBlueprint.route('/dag-runs/<dag_id>', methods=['POST'])
@csrf.exempt
def trigger_dag_api(dag_id):
    """Adds API endpoint to trigger DAG runs

    Accepts JSON for post that is added to a DAG run conf
    """
    session = settings.Session()

    dag_exists = session.query(DagModel).filter(DagModel.dag_id == dag_id).count()
    if not dag_exists:
        return Response('DAG {} does not exist'.format(dag_id), httplib.BAD_REQUEST)

    execution_date = datetime.now()

    json_params = request.get_json()

    dag = DAGs.get(dag_id)
    if not dag:
        return Response('Missing configuration for {} DAG'.format(dag_id), httplib.BAD_REQUEST)
    obj_id = json_params[dag.id_field]

    run_id = 'api_trigger_{}_{}'.format(obj_id, execution_date.isoformat())

    conf = json.dumps(json_params)
    dag_args = DagArgs(dag_id=dag_id, conf=conf, run_id=run_id, exec_date=execution_date)
    trigger_dag(dag_args)
    return jsonify(dag_id=dag_id, run_id=run_id)


class APIPlugin(AirflowPlugin):
    name = "trigger_dag_plugin"
    flask_blueprints = [APIBlueprint]
