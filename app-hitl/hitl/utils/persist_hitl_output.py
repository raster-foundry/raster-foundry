import json
import logging
import os

from utils.io import get_session

logger = logging.getLogger(__name__)
HOST = os.getenv("RF_HOST")

# Validated tasks should not be updated
# Machine labels fallen into validated tasks should not be posted
# Use label POST endpoint to append to existing labels
def persist_hitl_output(project_id, tasks, labels):
    """Post HITL results back to the DB.

    Args:
        project_id (str): ID of an annotation project
        tasks (FeatureCollection): tasks need to be updated
        labels (FeatureCollection): machine labels
    """
    update_tasks(project_id, tasks)
    add_labels(project_id, labels)

def update_tasks(project_id, tasks):
    for task in tasks["features"]:
        task_id = task["id"]
        url = f"{HOST}/api/annotation-projects/{project_id}/tasks/{task_id}"
        session = get_session()
        response = session.put(url, json=json.dumps(task))
        try:
            response.raise_for_status()
        except:
            logger.exception(
                f"Unable to update: {response.text} with {task.to_dict()}"
            )
            raise
        return response

def add_labels(project_id, labels):
    for label in labels["features"]:
        task_id = label["properties"]["annotationTaskId"]
        url = f"{HOST}/api/annotation-projects/{project_id}/tasks/{task_id}/labels"
        session = get_session()
        response = session.post(url, json=json.dumps(label))
        try:
            response.raise_for_status()
        except:
            logger.exception(f"Unable to POST labels via API: {response.text}")
            logger.exception(f"Attempted to POST: \n{label}\n")
            logger.exception(f"Response was: {response.content}")
            raise
        return response.json()
