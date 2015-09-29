# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

# Eventually we'll need this.
from apps.core import models  # NOQA
from django.conf import settings

import boto.sqs
from boto.sqs.message import Message


class QueueProcessor(object):
    """
    Encapsulates behavior for connecting to an SQS queue and moving image data
    throught the processing pipeline by adding new messages to the same queue.
    """
    def __init__(self):
        self.q = self.get_queue()

    def validate_image(self, data):
        # Pass image to Gdal to verify.
        payload = {
            'body': 'Image validate success. Send to reproject.',
            'data': {
                'type': {
                    'data_type': 'String',
                    'string_value': 'reproject'
                },
                'url': {
                    'data_type': 'String',
                    'string_value': 'http://path/to/file'
                }
            }
        }
        self.post_to_queue(payload)
        return True

    def reproject(self, data):
        # Pass image to Gdal to verify.
        payload = {
            'body': 'Reproject success. Send to EMR.',
            'data': {
                'type': {
                    'data_type': 'String',
                    'string_value': 'emr_handoff'
                },
                'urls': {
                    'data_type': 'String',
                    'string_value': 'http://path/to/file1,http://path/to/file2,http://path/to/file3'  # NOQA
                }
            }
        }
        self.post_to_queue(payload)
        return True

    def emr_hand_off(self, data):
        # Send data to EMR for processing.
        payload = {
            'body': 'EMR success. Send to save.',
            'data': {
                'type': {
                    'data_type': 'String',
                    'string_value': 'emr_complete'
                },
                'thumbnail': {
                    'data_type': 'String',
                    'string_value': 'http://path/to/file'
                },
                'tiles': {
                    'data_type': 'String',
                    'string_value': 'http://path/to/tiles'
                }
            }
        }
        self.post_to_queue(payload)
        return True

    def save_emr_result(self, data):
        # Update our server with data necessary for tile serving.
        # Nothing else needs to go in the queue.
        return True

    def get_queue(self):
        queue_name = settings.AWS_SQS_QUEUE
        queue_region = settings.AWS_SQS_REGION
        conn = boto.sqs.connect_to_region(queue_region)
        return conn.get_queue(queue_name)

    def post_to_queue(self, payload):
        m = Message()
        m.set_body(payload['body'])
        m.message_attributes = payload['data']
        self.q.write(m)
