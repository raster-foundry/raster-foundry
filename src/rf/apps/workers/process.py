# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division


import math
import time
from datetime import datetime

from apps.core.models import LayerImage, Layer
from apps.workers.image_validator import ensure_band_count
from apps.workers.sqs_manager import SQSManager
import apps.core.enums as enums


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

# Error messages
ERROR_MESSAGE_LAYER_IMAGE_INVALID = 'Cannot process invalid images.'
ERROR_MESSAGE_IMAGE_NOT_VALID = 'Image was not a valid GeoTiff file.'
ERROR_MESSAGE_IMAGE_TOO_FEW_BANDS = 'Image must have three or more bands.'
ERROR_MESSAGE_JOB_TIMEOUT = 'Layer could not be processed. ' + \
                            'The job timed out after ' + \
                            str(math.ceil(TIMEOUT_SECONDS / 60)) + ' minutes.'


class QueueProcessor(object):
    """
    Encapsulates behavior for connecting to an SQS queue and moving image data
    through the processing pipeline by adding new messages to the same queue.
    """
    def __init__(self):
        self.queue = SQSManager()

    def start_polling(self):
        while True and self.queue:
            s3_message = self.queue.get_message()
            if s3_message:
                delete_message = False
                message = s3_message['body']
                if 'Records' in message and message['Records'][0]:
                    record = message['Records'][0]
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
                            delete_message = self._validate_image(record)

                if delete_message:
                    self.queue.remove_message(s3_message)

    def _validate_image(self, data):
        """
        Use Gdal to verify an image is properly formatted and can be further
        processed.
        data -- attribute data from SQS.
        """
        def mark_image_valid(s3_uuid):
            image = LayerImage.objects.get(s3_uuid=s3_uuid)
            image.status = enums.STATUS_VALID
            image.save()
            layer_id = image.layer_id

            data = {'s3_uuid': s3_uuid}
            self.queue.add_message(JOB_REPROJECT, data)

            # Add a message to the queue that we can use to watch for timeouts.
            data = {
                'timeout': time.time() + TIMEOUT_SECONDS,
                'layer_id': layer_id
            }
            self.queue.add_message(JOB_TIMEOUT, data, TIMEOUT_DELAY)
            return True

        def mark_image_invalid(s3_uuid, error_message):
            image = LayerImage.objects.get(s3_uuid=s3_uuid)
            image.status = enums.STATUS_INVALID
            image.error = error_message
            image.save()
            layer_id = image.layer_id
            layer = Layer.objects.get(id=layer_id)
            layer.error = ERROR_MESSAGE_LAYER_IMAGE_INVALID
            layer.status = enums.STATUS_FAILED
            layer.status_updated_at = datetime.now()
            layer.save()
            return True

        key = data['s3']['object']['key']
        s3_uuid = self.extract_uuid_from_aws_key(key)
        byte_range = '0-1000000'  # Get first Mb(ish) of bytes.

        # Image validator thros AttributeError if it cannot read the image.
        try:
            if ensure_band_count(key, byte_range):
                return mark_image_valid(s3_uuid)
            else:
                error_message = ERROR_MESSAGE_IMAGE_TOO_FEW_BANDS
                return mark_image_invalid(s3_uuid, error_message)
        except AttributeError:
            error_message = ERROR_MESSAGE_IMAGE_NOT_VALID
            return mark_image_invalid(s3_uuid, error_message)

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
        self.queue.add_message(JOB_HANDOFF, data)
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
            layer.status_updated_at = datetime.now()
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
            if layer.status != enums.STATUS_COMPLETED:
                layer.status = enums.STATUS_FAILED
                layer.error = ERROR_MESSAGE_JOB_TIMEOUT
                layer.status_updated_at = datetime.now()
                layer.save()
        else:
            # Requeue the timeout message.
            self.queue.add_message(JOB_TIMEOUT, data, TIMEOUT_DELAY)

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
        layer.status = enums.STATUS_COMPLETED
        layer.status_updated_at = datetime.now()
        layer.save()
        # Nothing else needs to go in the queue. We're done.
        return True

    def extract_uuid_from_aws_key(self, key):
        """
        Given an AWS key, find the uuid and return it.
        AWS keys are a user id appended to a uuid with a file extension.
        EX: 10-1aa064aa-1086-4ff1-a90b-09d3420e0343.tif
        """
        dot = key.find('.') if key.find('.') >= 0 else len(key)
        first_dash = key.find('-')
        return key[first_dash:dot]
