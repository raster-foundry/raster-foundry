# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import math
import time
import uuid
import logging

from django.conf import settings

from apps.core.models import LayerImage, Layer
from apps.workers.image_validator import ImageValidator
from apps.workers.sqs_manager import SQSManager
from apps.workers.thumbnail import make_thumbs_for_layer
from apps.workers.emr import create_cluster, check_cluster_status
import apps.core.enums as enums
import apps.workers.status_updates as status_updates
from apps.workers.copy_images import s3_copy

log = logging.getLogger(__name__)

TIMEOUT_SECONDS = (60 * 60 * 5)  # 5 Hours.

JOB_COPY_IMAGE = 'copy_image'
JOB_VALIDATE = [
    'ObjectCreated:CompleteMultipartUpload',
    'ObjectCreated:Put',
    'ObjectCreated:Post',
    'ObjectCreated:Copy'
]
JOB_THUMBNAIL = 'thumbnail'
JOB_HANDOFF = 'emr_handoff'
JOB_TIMEOUT = 'timeout'
JOB_HEARTBEAT = 'emr_heartbeat'
JOB_CHUNK = 'chunk'
JOB_MOSAIC = 'mosaic'

STARTED = 'STARTED'
FINISHED = 'FINISHED'
FAILED = 'FAILED'

MAX_WAIT = 20  # Seconds.
TIMEOUT_DELAY = 300

# Error messages
ERROR_MESSAGE_THUMBNAIL_FAILED = 'Thumbnail generation failed.'
ERROR_MESSAGE_IMAGE_TRANSFER = 'Transferring image failed.'
ERROR_MESSAGE_JOB_TIMEOUT = 'Layer could not be processed. ' + \
                            'The job timed out after ' + \
                            str(math.ceil(TIMEOUT_SECONDS / 60)) + ' minutes.'
ERROR_MESSAGE_EMR_DEAD = 'Processing failed.'
ERROR_MESSAGE_UNSUPPORTED_MIME = 'Only GeoTiffs are supported.'
ERROR_MESSAGE_IMAGE_NOT_VALID = 'Invalid GeoTiff file.'
ERROR_MESSAGE_IMAGE_TOO_FEW_BANDS = 'Image does not have at least 3 bands.'


class QueueProcessor(object):
    """
    Encapsulates behavior for connecting to an SQS queue and moving image data
    through the processing pipeline by adding new messages to the same queue.
    """
    def __init__(self):
        self.queue = SQSManager()

    def start_polling(self):
        while True:
            message = self.queue.get_message()
            if not message:
                continue

            record = message['payload']

            # Flatten S3 event.
            if 'Records' in record and record['Records'][0]:
                record = record['Records'][0]

            delete_message = self.handle_message(record)
            if delete_message:
                self.queue.remove_message(message)

    def handle_message(self, record):
        # Parse record fields.
        if self.is_test_event(record):
            return True

        try:
            if 'eventName' in record:
                job_type = record['eventName']
                event_source = record['eventSource']
            elif 'stage' in record:
                job_type = record['stage']
            else:
                raise KeyError('eventName and stage missing')
        except KeyError:
            log.exception('Missing fields in message %s', record)
            return False

        log.info('Executing job %s', job_type)

        if job_type == JOB_THUMBNAIL:
            return self.thumbnail(record)
        elif job_type == JOB_HANDOFF:
            return self.emr_hand_off(record)
        elif job_type == JOB_HEARTBEAT:
            return self.emr_heartbeat(record)
        elif job_type == JOB_TIMEOUT:
            return self.check_timeout(record)
        elif job_type in JOB_VALIDATE:
            if event_source == 'aws:s3':
                return self.validate_image(record)
        elif job_type == JOB_COPY_IMAGE:
            return self.copy_image(record)
        elif job_type == JOB_CHUNK:
            return self.chunk(record)
        elif job_type == JOB_MOSAIC:
            return self.mosaic(record)

        return False

    def is_test_event(self, record):
        return 'Event' in record and record['Event'] == 's3:TestEvent'

    def validate_image(self, record):
        """
        Use Gdal to verify an image is properly formatted and can be further
        processed.
        record -- attribute data from SQS.
        """
        try:
            key = record['s3']['object']['key']
        except KeyError:
            return False

        s3_uuid = extract_uuid_from_aws_key(key)
        try:
            uuid.UUID(s3_uuid)
        except ValueError:
            return False

        # Ignore thumbnails.
        if not LayerImage.objects.filter(s3_uuid=s3_uuid).exists():
            return True

        # Image verification.
        validator = ImageValidator(settings.AWS_BUCKET_NAME, key)
        try:
            if not validator.image_format_is_supported():
                log.info('Image format not supported %s', key)
                return status_updates.mark_image_invalid(
                    s3_uuid, ERROR_MESSAGE_UNSUPPORTED_MIME)
            elif not validator.image_has_enough_bands():
                log.info('Not enough bands %s', key)
                return status_updates.mark_image_invalid(
                    s3_uuid, ERROR_MESSAGE_IMAGE_TOO_FEW_BANDS)
        except RuntimeError:
            log.exception('GDAL was unable to open %s', key)
            return status_updates.mark_image_invalid(
                s3_uuid, ERROR_MESSAGE_IMAGE_NOT_VALID)

        updated = status_updates.mark_image_valid(s3_uuid)
        if updated:
            log.info('Image validated %s', key)
            layer_id = status_updates.get_layer_id_from_uuid(s3_uuid)
            layer_images = LayerImage.objects.filter(layer_id=layer_id)
            # TODO: Filter `layer_images` instead of extra query.
            valid_images = LayerImage.objects.filter(
                layer_id=layer_id,
                status=enums.STATUS_VALID)

            all_valid = len(layer_images) == len(valid_images)
            layer_has_images = len(layer_images) > 0

            log.info('%d/%d images validated for layer %d',
                     len(valid_images),
                     len(layer_images),
                     layer_id)

            if all_valid and layer_has_images:
                log.info('Layer %d is valid', layer_id)
                status_updates.update_layer_status(
                    layer_id,
                    enums.STATUS_VALIDATED)

                data = {'layer_id': layer_id}

                log.info('Queue handoff job')
                self.queue.add_message(JOB_HANDOFF, data)

                log.info('Queue thumbnail job')
                self.queue.add_message(JOB_THUMBNAIL, data)

                # Add a message to the queue to watch for timeouts.
                data = {
                    'timeout': time.time() + TIMEOUT_SECONDS,
                    'layer_id': layer_id
                }
                log.info('Queue timeout job')
                self.queue.add_message(JOB_TIMEOUT, data, TIMEOUT_DELAY)
                return True
        return False

    def thumbnail(self, record):
        """
        Generate thumbnails for all images.
        record -- attribute data from SQS.
        """
        try:
            data = record['data']
            layer_id = data['layer_id']
        except KeyError:
            return False

        try:
            layer_id = int(layer_id)
        except ValueError:
            return False

        log.info('Generating thumbnails for layer %d...', layer_id)

        if make_thumbs_for_layer(layer_id):
            return True
        else:
            log.info('Failed to thumbnail layer %d', layer_id)
            status_updates.update_layer_status(layer_id,
                                               enums.STATUS_FAILED,
                                               ERROR_MESSAGE_THUMBNAIL_FAILED)
            return False

    def emr_hand_off(self, record):
        """
        Passes layer imgages to EMR to begin creating custom rasters.
        record -- attribute data from SQS.
        """
        try:
            data = record['data']
            layer_id = data['layer_id']
        except KeyError:
            return False

        try:
            layer_id = int(layer_id)
        except ValueError:
            return False

        layer = Layer.objects.get(id=layer_id)
        status_updates.update_layer_status(layer.id,
                                           enums.STATUS_PROCESSING)
        log.info('Launching EMR cluster for layer %d', layer_id)
        emr_response = create_cluster(layer)
        self.start_health_check(layer_id, emr_response)
        return True

    def emr_heartbeat(self, record):
        try:
            data = record['data']
            job_id = data['job_id']
            layer_id = data['layer_id']
        except KeyError:
            return False

        try:
            layer_id = int(layer_id)
        except ValueError:
            return False

        try:
            layer = Layer.objects.get(id=layer_id)
        except Layer.DoesNotExist:
            return False

        log.debug('Heartbeat for layer %d', layer_id)

        if layer.status in (enums.STATUS_COMPLETED, enums.STATUS_FAILED):
            log.debug('Ending heartbeat. Job is complete.')
            return True

        if not check_cluster_status(job_id):
            log.info('EMR job for layer %d has failed!', layer_id)
            return status_updates.update_layer_status(layer_id,
                                                      enums.STATUS_FAILED,
                                                      ERROR_MESSAGE_EMR_DEAD)

        elif layer.status != enums.STATUS_COMPLETED and \
                layer.status != enums.STATUS_FAILED:
            data = {'layer_id': layer_id, 'job_id': job_id}
            self.queue.add_message(JOB_HEARTBEAT, data, TIMEOUT_DELAY)

        return True

    def check_timeout(self, record):
        """
        Check an EMR job to see if it has completed before the timeout has
        been reached.
        record -- attribute data from SQS.
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
            layer_id = int(layer_id)
            timeout = float(timeout)
        except ValueError:
            return False

        try:
            layer = Layer.objects.get(id=layer_id)
        except Layer.DoesNotExist:
            return True

        log.debug('Timeout for layer %d', layer_id)

        if layer.status in (enums.STATUS_COMPLETED, enums.STATUS_FAILED):
            log.debug('Ending timeout. Job is complete.')
            return True

        if time.time() > timeout:
            log.info('Layer %d timed out!', layer_id)
            return status_updates.update_layer_status(
                layer_id,
                enums.STATUS_FAILED,
                ERROR_MESSAGE_JOB_TIMEOUT)

        self.queue.add_message(JOB_TIMEOUT, data, TIMEOUT_DELAY)
        return True

    def copy_image(self, record):
        """
        Copy an image into the S3 bucket from an external source.
        data -- attribute data from SQS.
        """
        log.info('Copying S3 image...')

        try:
            data = record['data']
            image_id = data['image_id']
        except KeyError:
            return False

        try:
            image_id = int(image_id)
        except ValueError:
            return False

        try:
            image = LayerImage.objects.get(id=image_id)
        except LayerImage.DoesNotExist:
            return False

        if image.source_s3_bucket_key:
            success = s3_copy(image.source_s3_bucket_key, image.get_s3_key())
            if success:
                log.info('Image copied successfully')
                return True
            else:
                return status_updates.mark_image_invalid(
                    image.s3_uuid, ERROR_MESSAGE_IMAGE_TRANSFER)
        else:
            return False

    def emr_event(self, record, step_name, started_status, finished_status):
        """
        Update status of layer based on EMR events.
        record -- attribute data from SQS.
        started_status -- status when job has started
        finished_status -- status when job has finished
        """
        try:
            layer_id = record['jobId']
            status = record['status']
        except KeyError:
            return False

        try:
            layer_id = int(layer_id)
        except ValueError:
            return False

        log.info('%s layer %d %s', step_name, layer_id, status)

        if status == FAILED:
            error = record.get('error', '')
            return status_updates.update_layer_status(layer_id,
                                                      enums.STATUS_FAILED,
                                                      error)
        elif status == STARTED:
            return status_updates.update_layer_status(layer_id,
                                                      started_status)
        elif status == FINISHED:
            return status_updates.update_layer_status(layer_id,
                                                      finished_status)
        else:
            return False

    def chunk(self, record):
        """
        Update status of layer based on chunk events.
        record -- attribute data from SQS.
        """
        return self.emr_event(record, 'Chunk',
                              enums.STATUS_CHUNKING, enums.STATUS_CHUNKED)

    def mosaic(self, record):
        """
        Update status of layer based on mosaic events.
        record -- attribute data from SQS.
        """
        return self.emr_event(record, 'Mosaic',
                              enums.STATUS_MOSAICKING, enums.STATUS_COMPLETED)

    def start_health_check(self, layer_id, emr_response):
        try:
            job_id = emr_response['JobFlowId']
        except KeyError:
            return False

        data = {'job_id': job_id, 'layer_id': layer_id}
        self.queue.add_message(JOB_HEARTBEAT, data, TIMEOUT_DELAY)


def extract_uuid_from_aws_key(key):
    """
    Given an AWS key, find the uuid and return it.
    AWS keys are a user id appended to a uuid with a file extension.
    EX: 10-1aa064aa-1086-4ff1-a90b-09d3420e0343.tif
    """
    dot = key.find('.') if key.find('.') >= 0 else len(key)
    first_dash = key.find('-')
    return key[first_dash:dot]
