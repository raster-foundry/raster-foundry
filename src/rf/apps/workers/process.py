# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from apps.core.models import Layer, LayerImage
from django.conf import settings

import boto.sqs
from boto.sqs.message import Message


class QueueProcessor(object):
    """
    Encapsulates behavior for connecting to an SQS queue and moving image data
    through the processing pipeline by adding new messages to the same queue.
    """
    def __init__(self):
        self.q = self.get_queue()

    def validate_image(self, data):
        # Pass image to Gdal to verify.
        # If it succeeds continue else return False.

        # TODO Get this from the data pulled out of the queued item.
        payload_uuid = 'ad352e82-3f6b-42d6-96b0-dee22affe884'  # NOQA - JUST FOR TESTING.

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
                },
                's3_uuid': {
                    'data_type': 'String',
                    'string_value': payload_uuid
                }
            }
        }
        self.post_to_queue(payload)

        image = LayerImage.objects.get(s3_uuid=payload_uuid)
        image.verified = True
        image.save()
        return True

    def reproject(self, data):
        # Pass image to Gdal to verify.
        # If it succeeds continue else return False.

        # TODO Get this from the data pulled out of the queued item.
        payload_uuid = 'ad352e82-3f6b-42d6-96b0-dee22affe884'  # NOQA - JUST FOR TESTING.

        image = LayerImage.objects.get(s3_uuid=payload_uuid)
        image.reprojected = True
        image.save()
        layer_id = image.layer_id
        layers = LayerImage.objects.filter(layer_id=layer_id)
        reprojected_layers = LayerImage.objects.filter(layer_id=layer_id,
                                                       reprojected=True)
        ready_to_process = len(layers) == len(reprojected_layers)

        if ready_to_process:
            images_in_layer = ','.join(str(l.source_uri) for l in layers)
            payload = {
                'body': 'Reproject success. Send to EMR.',
                'data': {
                    'type': {
                        'data_type': 'String',
                        'string_value': 'emr_handoff'
                    },
                    'urls': {
                        'data_type': 'String',
                        'string_value': images_in_layer
                    },
                    'layer_id': {
                        'data_type': 'Number',
                        'string_value': layer_id
                    }
                }
            }
            self.post_to_queue(payload)

        return True

    def emr_hand_off(self, data):
        # Send data to EMR for processing.
        # return False if it fails to start.
        # Get some id that can be used to check on job status.

        # TODO Get this from the data pulled out of the queued item.
        job_id = '01234567890'  # JUST FOR TESTING.
        layer_id = 3  # JUST FOR TESTING

        payload = {
            'body': 'EMR job started.',
            'data': {
                'type': {
                    'data_type': 'String',
                    'string_value': 'emr_job_staus'
                },
                'job_id': {
                    'data_type': 'Number',
                    'string_value': job_id
                },
                'layer_id': {
                    'data_type': 'Number',
                    'string_value': layer_id
                }
            }
        }
        self.post_to_queue(payload)
        return True

    def check_emr_status_and_save_result(self, data):
        # Update our server with data necessary for tile serving.

        # TODO Get this from the data pulled out of the queued item.
        job_id = '01234567890'  # NOQA - JUST FOR TESTING.
        layer_id = 3  # Just FOR TESTING
        job_done = True  # JUST FOR TESTING. Check with EMR for real value.

        if job_done:
            layer = Layer.objects.get(id=layer_id)
            layer.processed = True
            layer.save()
            # Nothing else needs to go in the queue. We're done.
            return True

        # If the job is not done, return false so the item goes back in the
        # queue. We will pick it up again and test again later.
        return False

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
