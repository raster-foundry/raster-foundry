import logging
import os

import rollbar

logger = logging.getLogger(__name__)

environment = os.getenv('ENVIRONMENT')
rollbar_token = os.getenv('ROLLBAR_SERVER_TOKEN')

# Avoid use of threads by default in case the worker exits before sending message
rollbar.init(rollbar_token, environment, handler='blocking')


def failure_callback(context):
    """Failure callback used to send notifications to rollbar for failed task instances

    Args:
        context (dict): contextual information passed by airflow to the callback
    """

    dag_run = context['dag_run']
    task = context['task']

    task_id = task.task_id
    dag_id = dag_run.dag_id
    dag_run_id = dag_run.run_id

    message = 'Task {} failed for DAG {} (DAG run: {})'.format(
        task_id, dag_id, dag_run_id
    )
    notify_rollbar(message)


def notify_rollbar(message):
    """Function to notify rollbar of an error if configured

    Args:
        message (str): message to send to rollbar
    """

    if all([environment, rollbar_token]):
        rollbar.report_message(message)
    else:
        logger.warning('Both ENVIRONMENT and ROLLBAR_SERVER_TOKEN must be set to log exceptions to rollbar')
        logger.error(message)
