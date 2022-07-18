import logging
import os

from utils.io import get_session
from models.hitl_job import HITLJob
from models.annotation_project import AnnotationProject

logger = logging.getLogger(__name__)
HOST = os.getenv("RF_HOST")


def get_label_classes(campaign_id):
    """Get class definition of a campaign

    Args:
        campaign_id (str): ID of a campaign
    """
    url = f"{HOST}/api/campaigns/{campaign_id}/label-class-groups"
    session = get_session()
    response = session.get(url)
    response.raise_for_status()
    groups = response.json()
    classes = list()
    for group in groups:
        group_id = group["id"]
        url = f"{HOST}/api/campaigns/{campaign_id}/label-class-groups/{group_id}/label-classes"
        session = get_session()
        resp = session.get(url)
        resp.raise_for_status()
        classes.append(resp.json())
    return classes

def paginate_tasks(project_id, page):
    """Get paginated tasks

    Args:
        project_id (str): ID of an annotation project
        page (int): 0-indexed page number
    """
    url = f"{HOST}/api/annotation-projects/{project_id}/tasks?page={page}"
    session = get_session()
    response = session.get(url)
    response.raise_for_status()
    return response.json()

def get_tasks(project_id):
    """Get all tasks from an annotation project in a FeatureCollection

    Args:
        project_id (str): ID of an annotation project
    """
    result = {
        "type": "FeatureCollection",
        "features": list()
    }
    has_next = True
    page = 0
    while has_next:
        resp = paginate_tasks(project_id, page)
        result["features"] += resp["features"]
        has_next = resp["hasNext"]
        page += 1
    return result

def get_validated_task_ids(tasks):
    result = list()
    for task in tasks["features"]:
        if task["properties"]["status"] == "VALIDATED":
            result.append(task["id"])
    return result

def get_labels(project_id, task_ids):
    result = {
        "type": "FeatureCollection",
        "features": list()
    }
    for task_id in task_ids:
        url = f"{HOST}/api/annotation-projects/{project_id}/tasks/{task_id}/labels"
        session = get_session()
        response = session.get(url)
        response.raise_for_status()
        labels = response.json()
        result["features"] += labels
    return result

def get_scene(project_id):
    project = AnnotationProject.from_id(project_id)
    url = f"{HOST}/api/projects/{project.projectId}/scenes"
    session = get_session()
    response = session.get(url)
    response.raise_for_status()
    scenes = response.json()
    return scenes["results"][0]

def get_input(job_id):
    """Run image, task grid, labels, and class definition

    Args:
        job_id (str): ID of a HITL Job
    """
    logger.info("Getting HITL job record")
    job = HITLJob.from_id(job_id)
    logger.info(f"HITL job for campaign {job.campaignId}, project {job.projectId}, user {job.owner}")
    logger.info("Updating HITL job status to RUNNING")
    job.update_job_status("RUNNING")
    logger.info("Getting label classes")
    label_classes = get_label_classes(job.campaignId)
    logger.info("Getting task grid")
    tasks = get_tasks(job.projectId)
    logger.info("Getting human labels")
    validated_task_ids = get_validated_task_ids(tasks)
    labels = get_labels(job.projectId, validated_task_ids)
    logger.info("Getting image")
    scene = get_scene(job.projectId)
    return scene, tasks, labels, label_classes, job