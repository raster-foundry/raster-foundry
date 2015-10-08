# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from apps.core.models import LayerImage, Layer
import apps.core.enums as enums
from django.conf import settings

import time
import json
import boto.sqs
from boto.sqs.message import Message

TIMEOUT_SECONDS = (60 * 10)  # 10 Minutes.

JOB_VALIDATE = [
    'ObjectCreated:CompleteMultipartUpload',
    'ObjectCreated:Put',
    'ObjectCreated:Post'
]
JOB_REPROJECT = 'reproject'
JOB_HANDOFF = 'emr_handoff'
JOB_TIMEOUT = 'timeout'

MAX_WAIT = 20  # Seconds.
DEFAULT_DELAY = 0
TIMEOUT_DELAY = 60


class QueueProcessor(object):
    """
    Encapsulates behavior for connecting to an SQS queue and moving image data
    through the processing pipeline by adding new messages to the same queue.
    """
    def __init__(self):
        self.q = self.get_queue()

    def start_polling(self):
        while True and self.q:
            message = self.q.read(wait_time_seconds=MAX_WAIT,
                                  message_attributes=['All'])

            if message:
                delete_message = False
                body = json.loads(message.get_body())
                if 'Records' in body and body['Records'][0]:
                    record = body['Records'][0]
                    job_type = record['eventName']
                    if job_type == JOB_REPROJECT:
                        delete_message = self.reproject(record['data'])
                    elif job_type == JOB_HANDOFF:
                        # May want to keep the message from showing back up
                        # on the queue by setting a new visability with
                        # message.change_visibility()
                        delete_message = self.emr_hand_off(record['data'])
                    elif job_type == JOB_TIMEOUT:
                        delete_message = self.check_timeout(record['data'])
                    elif job_type in JOB_VALIDATE:
                        if record['eventSource'] == 'aws:s3':
                            delete_message = self.validate_image(record)

                if delete_message:
                    self.q.delete_message(message)

    def validate_image(self, data):
        """
        Use Gdal to verify an image is properly formatted and can be further
        processed.
        data -- attribute data from SQS.
        """
        # Pass image to Gdal to verify.
        # If it succeeds continue else return False.

        key = data['s3']['object']['key']
        payload_uuid = self.extract_uuid_from_aws_key(key)

        url = self.make_s3_url(data['s3']['bucket']['name'], key)

        # TODO - Right now we don't save data to the DB with upload. So hard
        # code something for testing.
        try:
            image = LayerImage.objects.get(s3_uuid=payload_uuid)
            image.status = enums.STATUS_VALIDATED
            image.save()
            layer_id = image.layer_id
        except:
            payload_uuid = '1aa064aa-1086-4ff1-a90b-09d3420e0343'
            layer_id = 2

        data = {'url': url, 's3_uuid': payload_uuid}
        payload = self.make_payload(JOB_REPROJECT, data)
        self.post_to_queue(payload)

        # Add a message to the queue that we can use to watch for timeouts.
        data = {
            'timeout': time.time() + TIMEOUT_SECONDS,
            'layer_id': layer_id
        }
        payload = self.make_payload(JOB_TIMEOUT, data)
        self.post_to_queue(payload, TIMEOUT_DELAY)
        return True

    def reproject(self, data):
        """
        Reproject incoming image to Web Mercator.
        data -- attribute data from SQS.
        """
        # Pass image to Gdal to reproject
        # If it succeeds continue else return False.

        s3_uuid = data['s3_uuid']
        image = LayerImage.objects.get(s3_uuid=s3_uuid)
        image.status = enums.STATUS_REPROJECTED
        image.save()

        data = {'layer_id': image.layer_id}
        payload = self.make_payload(JOB_HANDOFF, data)
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

        layer_id = data['layer_id']
        layer_images = LayerImage.objects.filter(layer_id=layer_id)
        reprojected_images = LayerImage.objects.filter(
            layer_id=layer_id,
            status=enums.STATUS_REPROJECTED)
        ready_to_process = len(layer_images) == len(reprojected_images)

        if ready_to_process:
            layer = Layer.objects.get(id=layer_id)
            layer.status = enums.STATUS_PROCESSING
            layer.save()

            # POST TO EMR HERE.
        return True

    def check_timeout(self, data):
        """
        Check an EMR job to see if it has completed before the timeout has
        been reached.
        data -- attribute data from SQS.
        """

        layer_id = data['layer_id']
        timeout = data['timeout']
        if time.time() > timeout:
            layer = Layer.objects.get(id=layer_id)
            if layer.status != enums.STATUS_COMPLETE:
                layer.status = enums.STATUS_FAILED
                layer.save()
        else:
            # Requeue the timeout message.
            payload = self.make_payload(JOB_TIMEOUT, data)
            self.post_to_queue(payload, TIMEOUT_DELAY)

        return True

    def save_result(self, data):
        """
        When an EMR job has completed updates the associated model in the
        database.
        data -- attribute data from SQS.
        """

        # Update our server with data necessary for tile serving.

        layer_id = data['layer_id']
        layer = Layer.objects.get(id=layer_id)
        layer.status = enums.STATUS_COMPLETE
        layer.save()
        # Nothing else needs to go in the queue. We're done.
        return True

    def make_payload(self, job_type, data):
        return {
            'Records': [
                {
                    'eventSource': 'rf:boto',
                    'eventName': job_type,
                    'data': data
                }
            ]
        }

    def make_s3_url(self, bucket_name, key):
        return 'https://s3.amazonaws.com/' + bucket_name + '/' + key

    def extract_uuid_from_aws_key(self, key):
        """
        Given an AWS key, find the uuid and return it.
        AWS keys are a user id appended to a uuid with a file extension.
        EX: 10-1aa064aa-1086-4ff1-a90b-09d3420e0343.tif
        """
        dot = key.find('.') if key.find('.') >= 0 else len(key)
        first_dash = key.find('-')
        return key[first_dash:dot]

    def get_queue(self):
        """
        Get a connection to the SQS queue.
        """
        queue_name = settings.AWS_SQS_QUEUE
        queue_region = settings.AWS_SQS_REGION
        conn = boto.sqs.connect_to_region(queue_region)
        return conn.get_queue(queue_name)

    def post_to_queue(self, payload, delay=DEFAULT_DELAY):
        """
        Create an SQS message from a payload.
        data -- structured data representing the new SQS message.
        """
        m = Message()
        m.set_body(json.dumps(payload))
        self.q.write(m, delay)
