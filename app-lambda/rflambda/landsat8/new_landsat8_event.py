import json
import os
from typing import Dict, Any


class NewLandsat8Event(object):

    def __init__(self, bucket: str, prefix: str):
        self.bucket = bucket
        self.prefix = prefix
        self.scene_name = os.path.basename(self.prefix)
        self.mtl_json = os.path.join(self.prefix, self.scene_name + '_MTL.json')

    @classmethod
    def parse(cls, sourceEvent: Dict[Any, Any]):
        s3_event = json.loads(sourceEvent['Records'][0]['Sns']['Message'])
        bucket = s3_event['Records'][0]['s3']['bucket']['name']
        index_key = s3_event['Records'][0]['s3']['object']['key']
        prefix = os.path.dirname(index_key)
        return NewLandsat8Event(bucket, prefix)
