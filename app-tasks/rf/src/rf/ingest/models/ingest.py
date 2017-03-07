"""Python class representation of a Raster Foundry Ingest"""

import uuid
import json
import boto3
import os
import logging

s3 = boto3.resource('s3')
logger = logging.getLogger(__name__)


class Ingest(object):

    DATA_BUCKET = os.getenv('DATA_BUCKET')

    def __init__(self, id, layers):

        """
            Create a new Ingest

            Args:
                id (str): ID for ingest
                layers (List[Layer]): A list of all layers included in the ingest
        """

        assert len(layers), "An ingest requires at least one Layer"
        self.id = id or str(uuid.uuid4())
        self.layers = layers
        self.key_name = 'ingest-definitions/{}.json'.format(self.id)

    @classmethod
    def from_dict(cls, d):
        return cls(
            d.get('id'),
            d.get('layers')
        )

    def to_dict(self):
        """ Return a dict formatted specifically for serialization to an ingest definition """
        return {
            'id': self.id,
            'layers': [l.to_dict() for l in self.layers]
        }

    @property
    def s3_uri(self):
        """S3 URI for ingest location"""
        return 's3://{}/{}'.format(self.DATA_BUCKET, self.key_name)

    def put_in_s3(self):
        """Place ingest definition in S3

        Returns:
            s3.Object
        """
        bucket = s3.Bucket(self.DATA_BUCKET)
        logger.info('Putting ingest definition %s to %s', self.id, self.s3_uri)
        object = bucket.put_object(
            Key=self.key_name, Body=json.dumps(self.to_dict()) ,ContentType='application/json'
        )
        logger.info('Successfully pushed ingest definition %s to %s', self.id, self.s3_uri)
        return object
