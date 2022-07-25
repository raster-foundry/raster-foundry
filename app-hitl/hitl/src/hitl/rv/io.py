from typing import List
import json
import geopandas as gpd

from rastervision.core.data import ClassConfig


def get_img_info(uri: str) -> dict:
    with open(uri, 'r') as f:
        img_info = json.load(f)['results'][0]
    return img_info


def get_task_grid(uri: str) -> gpd.GeoDataFrame:
    # TODO
    pass


def get_class_config(label_classes: List[dict]) -> ClassConfig:
    names = [c['name'] for c in label_classes]
    colors = [c['colorHexCode'] for c in label_classes]
    class_config = ClassConfig(
        names=['background'] + names,
        colors=['black'] + colors,
        null_class='background')
    return class_config
