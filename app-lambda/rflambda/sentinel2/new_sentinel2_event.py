import json
from typing import Dict, Any


class NewSentinel2Event(object):

    def __init__(self, scene_name: str, prefix: str, product_prefix: str):
        self.bucket = 'sentinel-s2-l1c'
        self.prefix = prefix
        self.scene_name = f'S2 {scene_name}'
        self.tile_info_json = f's3://{self.bucket}/{self.prefix}/tileInfo.json'
        self.product_info_json = f's3://{self.bucket}/{product_prefix}/productInfo.json'

    @classmethod
    def parse(cls, sourceEvent: Dict[Any, Any]):
        product_info = json.loads(sourceEvent['Records'][0]['Sns']['Message'])
        name = product_info['name']
        prefix = product_info['tiles'][0]['path']
        product_prefix = product_info['path']
        return NewSentinel2Event(name, prefix, product_prefix)
