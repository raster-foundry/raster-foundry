import json

import geopandas as gpd

from .merge_labels import merge_labels_with_task_grid

def post_process(task_grid_with_scores, pred_geojson_uri, label_classes, job_id):
    updated_tasks_dict, merged_preds_gdf = process_tasks(
        task_grid_with_scores,
        pred_geojson_uri
    )
    labels_to_post_dict = process_labels(merged_preds_gdf, label_classes, job_id)
    return updated_tasks_dict, labels_to_post_dict

def process_tasks(task_grid_with_scores: gpd.GeoDataFrame, pred_geojson_uri: str):
    # keep task cells not of status "VALIDATED"
    tasks_to_update = task_grid_with_scores.loc[
        task_grid_with_scores["status"] != "VALIDATED"
    ]
    tasks_to_update.set_index('id', inplace=True)
    # read RV predictions
    preds_gdf = gpd.read_file(pred_geojson_uri)
    # find predictions fall inside of these cells
    merged_preds_gdf = merge_labels_with_task_grid(
        preds_gdf, tasks_to_update)
    # update task status to "LABELED" where predictions fall in
    task_ids_to_update = list(set(merged_preds_gdf["taskId"]))
    tasks_to_update = tasks_to_update.loc[
        tasks_to_update["taskId"].isin(task_ids_to_update)
    ]
    tasks_to_update["status"] = "LABELED"
    updated_tasks_dict = json.loads(tasks_to_update.to_json())
    return updated_tasks_dict, merged_preds_gdf

def process_labels(merged_preds_gdf, label_classes, job_id):
    dissolved = merged_preds_gdf.dissolve(by="taskId").reset_index()
    dissolved = dissolved[["taskId", "geometry"]]
    dissolved["description"] = None
    dissolved["isActive"] = True
    dissolved["sessionId"] = None
    dissolved["score"] = None
    dissolved["hitlVersionId"] = job_id
    annotationLabelClasses = [c["id"] for c in label_classes]
    labels_to_post_dict = json.loads(dissolved.to_json())
    for label in labels_to_post_dict["features"]:
        label["properties"]["annotationLabelClasses"] = annotationLabelClasses
    return labels_to_post_dict