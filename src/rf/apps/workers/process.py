# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division


import math
import time
import uuid

from apps.core.models import LayerImage, Layer
from apps.workers.image_validator import ImageValidator
from apps.workers.sqs_manager import SQSManager
from apps.workers.thumbnail import make_thumbs_for_layer
import apps.core.enums as enums
import apps.workers.status_updates as status_updates

TIMEOUT_SECONDS = (60 * 10)  # 10 Minutes.

JOB_VALIDATE = [
    'ObjectCreated:CompleteMultipartUpload',
    'ObjectCreated:Put',
    'ObjectCreated:Post'
]
JOB_THUMBNAIL = 'thumbnail'
JOB_HANDOFF = 'emr_handoff'
JOB_TIMEOUT = 'timeout'

MAX_WAIT = 20  # Seconds.
DEFAULT_DELAY = 0
TIMEOUT_DELAY = 60

# Error messages
ERROR_MESSAGE_LAYER_IMAGE_INVALID = 'Cannot process invalid images.'
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
                    if job_type == JOB_THUMBNAIL:
                        delete_message = self._thumbnail(record)
                    elif job_type == JOB_HANDOFF:
                        delete_message = self._emr_hand_off(record)
                    elif job_type == JOB_TIMEOUT:
                        delete_message = self._check_timeout(record)
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
        try:
            key = data['s3']['object']['key']
        except KeyError:
            return False

        s3_uuid = self._extract_uuid_from_aws_key(key)
        try:
            uuid.UUID(s3_uuid)
        except ValueError:
            return False

        # Ignore thumbnails.
        if not LayerImage.objects.filter(s3_uuid=s3_uuid).exists():
            return True

        byte_range = '0-1000000'  # Get first Mb(ish) of bytes.
        # Image verification.
        validator = ImageValidator(key, byte_range)
        if not validator.image_format_is_supported():
            return status_updates.mark_image_invalid(
                s3_uuid, validator.get_error())
        elif not validator.image_has_min_bands(3):
            return status_updates.mark_image_invalid(
                s3_uuid, validator.get_error())
        else:
            updated = status_updates.mark_image_valid(s3_uuid)
            if updated:
                layer_id = status_updates.get_layer_id_from_uuid(s3_uuid)
                data = {'layer_id': layer_id}
                self.queue.add_message(JOB_HANDOFF, data)

                # Add a message to the queue to watch for timeouts.
                data = {
                    'timeout': time.time() + TIMEOUT_SECONDS,
                    'layer_id': layer_id
                }
                self.queue.add_message(JOB_TIMEOUT, data, TIMEOUT_DELAY)
            return updated

    def _thumbnail(self, record):
        """
        Generate thumbnails for all images.
        data -- attribute data from SQS.
        """
        try:
            data = record['data']
            layer_id = data['layer_id']
        except KeyError:
            return False

        try:
            int(layer_id)
        except ValueError:
            return False

        layer_id = data['layer_id']
        make_thumbs_for_layer(layer_id)

        data = {'layer_id': layer_id}
        self.queue.add_message(JOB_HANDOFF, data)

        return True

    def _emr_hand_off(self, record):
        """
        Passes layer imgages to EMR to begin creating custom rasters.
        data -- attribute data from SQS.
        """
        # Send data to EMR for processing.
        # return False if it fails to start.
        # EMR posts directly to queue upon competion.
        try:
            data = record['data']
            layer_id = data['layer_id']
        except KeyError:
            return False

        try:
            int(layer_id)
        except ValueError:
            return False

        layer_images = LayerImage.objects.filter(layer_id=layer_id)
        valid_images = LayerImage.objects.filter(layer_id=layer_id,
                                                 status=enums.STATUS_VALID)
        all_valid = len(layer_images) == len(valid_images)
        some_images = len(layer_images) > 0
        ready_to_process = some_images and all_valid

        if ready_to_process:
            return status_updates.update_layer_status(layer_id,
                                                      enums.STATUS_PROCESSING)
            # POST TO EMR HERE.
        else:
            return some_images

    def _check_timeout(self, record):
        """
        Check an EMR job to see if it has completed before the timeout has
        been reached.
        data -- attribute data from SQS.
        """
        try:
            data = record['data']
            layer_id = data['layer_id']
            timeout = data['timeout']
        except KeyError:
            # A bad message showed up. Leave it alone. Eventually it'll end
            # in the dead letter queue.
            return False

        try:
            int(layer_id)
            float(timeout)
        except ValueError:
            return False

        if time.time() > timeout:
            try:
                layer = Layer.objects.get(id=layer_id)
            except Layer.DoesNotExist:
                return False

            if layer is not None and layer.status != enums.STATUS_COMPLETED:
                return status_updates \
                    .update_layer_status(layer_id,
                                         enums.STATUS_FAILED,
                                         ERROR_MESSAGE_JOB_TIMEOUT)
        else:
            # Requeue the timeout message.
            self.queue.add_message(JOB_TIMEOUT, data, TIMEOUT_DELAY)

        return True

    def _save_result(self, record):
        """
        When an EMR job has completed updates the associated model in the
        database.
        data -- attribute data from SQS.
        """
        try:
            data = record['data']
            layer_id = data['layer_id']
        except KeyError:
            return False

        try:
            int(layer_id)
        except ValueError:
            return False

        # Update our server with data necessary for tile serving.
        # Nothing else needs to go in the queue. We're done.
        return status_updates.update_layer_status(data['layer_id'],
                                                  enums.STATUS_COMPLETED)

    def _extract_uuid_from_aws_key(self, key):
        """
        Given an AWS key, find the uuid and return it.
        AWS keys are a user id appended to a uuid with a file extension.
        EX: 10-1aa064aa-1086-4ff1-a90b-09d3420e0343.tif
        """
        dot = key.find('.') if key.find('.') >= 0 else len(key)
        first_dash = key.find('-')
        return key[first_dash:dot]
