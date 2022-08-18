from typing import List

from rastervision.core.data import ClassConfig


def get_class_config(label_classes: List[dict]) -> ClassConfig:
    names = [c['name'] for c in label_classes]
    colors = [c['colorHexCode'] for c in label_classes]
    class_config = ClassConfig(
        names=['background'] + names,
        colors=['black'] + colors,
        null_class='background')
    return class_config
