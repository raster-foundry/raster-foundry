from os.path import join
from typing import Tuple

import geopandas as gpd

from rastervision.core.data import ClassConfig

from .tasks import get_labeled_tasks
from .data import make_scene
from .train import train
from .predict import predict
from .score import compute_priority_scores


def active_learning_step(iter_num: int, class_config: ClassConfig,
                         img_info: dict, labels_uri: str,
                         task_grid: gpd.GeoDataFrame, output_dir: str,
                         last_output_dir: str, train_kw: dict,
                         predict_kw: dict) -> Tuple[gpd.GeoDataFrame, str]:
    labeled_task_polygons = get_labeled_tasks(task_grid)
    scene = make_scene(
        scene_id=f'scene-iter-{iter_num}',
        class_config=class_config,
        img_info=img_info,
        labels_uri=labels_uri,
        aoi_polygons=labeled_task_polygons)

    nlabeled_tasks = len(labeled_task_polygons)
    train_kw.update(dict(num_chips=(nlabeled_tasks * 20)))
    if iter_num > 0 and last_output_dir is not None:
        prev_model_weights = join(last_output_dir, 'train', 'last-model.pth')
        train_kw.update(dict(init_weights=prev_model_weights))
    train_dir = join(output_dir, 'train')
    learner = train(scene, class_config, output_dir=train_dir, **train_kw)

    pred_dir = join(output_dir, 'pred')
    labels, pred_geojson_uri = predict(
        learner, scene, class_config, output_dir=pred_dir, **predict_kw)
    task_grid_with_scores = compute_priority_scores(task_grid, labels,
                                                    scene.raster_source)

    return task_grid_with_scores, pred_geojson_uri
