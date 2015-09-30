# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from apps.core.models import LayerImage, LayerMeta
from django.conf import settings

import time
import boto.sqs
from boto.sqs.message import Message

TIMEOUT_SECONDS = (60 * 10)  # 10 Minutes.
STATUS_VALIDATED = 'validated'
STATUS_REPROJECTED = 'reprojected'
STATUS_PROCESSING = 'processing'
STATUS_FAILED = 'failed'
STATUS_COMPLETE = 'complete'


class QueueProcessor(object):
    """
    Encapsulates behavior for connecting to an SQS queue and moving image data
    through the processing pipeline by adding new messages to the same queue.
    """
    def __init__(self):
        self.q = self.get_queue()

    def validate_image(self, data):
        """
        Use Gdal to verify an image is properly formatted and can be further
        processed.
        data -- attribute data from SQS.
        """
        # Pass image to Gdal to verify.
        # If it succeeds continue else return False.

        # TODO Get this from the data pulled out of the queued item.
        payload_uuid = 'ad352e82-3f6b-42d6-96b0-dee22affe884'  # NOQA - JUST FOR TESTING.

        image = LayerImage.objects.get(s3_uuid=payload_uuid)
        image.status = STATUS_VALIDATED
        image.save()

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
        return True

    def reproject(self, data):
        """
        Reproject incoming image to Web Mercator.
        data -- attribute data from SQS.
        """
        # Pass image to Gdal to reproject
        # If it succeeds continue else return False.

        # TODO Get this from the data pulled out of the queued item.
        payload_uuid = 'ad352e82-3f6b-42d6-96b0-dee22affe884'  # NOQA - JUST FOR TESTING.

        image = LayerImage.objects.get(s3_uuid=payload_uuid)
        image.status = STATUS_REPROJECTED
        image.save()

        payload = {
            'body': 'Reproject success. Send to EMR.',
            'data': {
                'type': {
                    'data_type': 'String',
                    'string_value': 'emr_handoff'
                },
                'layer_id': {
                    'data_type': 'Number',
                    'string_value': image.layer_id
                }
            }
        }
        self.post_to_queue(payload)
        return True

    def emr_hand_off(self, data):
        """
        Passes layer imgages to EMR to begin creating custom rasters.
        data -- attribute data from SQS.
        """
        # If all the images are ready send data to EMR for processing.
        # return False if it fails to start.
        # EMR posts directly to queue upon competion.

        # TODO Get this from the data pulled out of the queued item.
        layer_id = 3  # JUST FOR TESTING
        layer_images = LayerImage.objects.filter(layer_id=layer_id)
        reprojected_images = LayerImage.objects.filter(layer_id=layer_id,
                                                       status=STATUS_REPROJECTED)  # NOQA
        ready_to_process = len(layer_images) == len(reprojected_images)

        if ready_to_process:
            layer_meta = LayerMeta.objects.get(layer_id=layer_id)
            layer_meta.state = STATUS_PROCESSING
            layer_meta.save()

            # POST TO EMR HERE.

            # Add a message to the queue that we can use to watch for
            # EMR timeout.
            payload = {
                'body': 'Sent to EMR. Waiting for result.',
                'data': {
                    'type': {
                        'data_type': 'String',
                        'string_value': 'emr_handoff'
                    },
                    'timeout': {
                        'data_type': 'Number',
                        'string_value': time.time() + TIMEOUT_SECONDS
                    },
                    'layer_id': {
                        'data_type': 'Number',
                        'string_value': layer_id
                    }
                }
            }
            self.post_to_queue(payload)

        return True

    def check_timeout(self, data):
        """
        Check an EMR job to see if it has completed before the timeout has
        been reached.
        data -- attribute data from SQS.
        """
        # TODO Get this from the data pulled out of the queued item.
        layer_id = 3  # JUST FOR TESTING.
        timeout = 1443640221  # JUST FOR TESTING.
        if time.time() > timeout:
            layer_meta = LayerMeta.objects.get(layer_id=layer_id)
            if not layer_meta.state == STATUS_COMPLETE:
                layer_meta.state = STATUS_FAILED
                layer_meta.save()
            return True
        else:
            return False

    def save_result(self, data):
        """
        When an EMR job has completed updates the associated model in the
        database.
        data -- attribute data from SQS.
        """
        # Update our server with data necessary for tile serving.

        # TODO Get this from the data pulled out of the queued item.
        layer_id = 3  # Just FOR TESTING

        layer_meta = LayerMeta.objects.get(id=layer_id)
        layer_meta.state = STATUS_COMPLETE
        layer_meta.save()
        # Nothing else needs to go in the queue. We're done.
        return True

    def get_queue(self):
        """
        Get a connection to the SQS queue.
        """
        queue_name = settings.AWS_SQS_QUEUE
        queue_region = settings.AWS_SQS_REGION
        conn = boto.sqs.connect_to_region(queue_region)
        return conn.get_queue(queue_name)

    def post_to_queue(self, payload):
        """
        Create an SQS message from a payload.
        data -- structured data representing the new SQS message.
        """
        m = Message()
        m.set_body(payload['body'])
        m.message_attributes = payload['data']
        self.q.write(m)
