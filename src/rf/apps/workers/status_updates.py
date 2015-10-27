# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division


from datetime import datetime

from apps.core.models import LayerImage, Layer
import apps.core.enums as enums

ERROR_MESSAGE_LAYER_IMAGE_INVALID = 'Cannot process invalid images.'


def mark_image_valid(s3_uuid):
    try:
        image = LayerImage.objects.get(s3_uuid=s3_uuid)
    except LayerImage.DoesNotExist:
        return False

    image.status = enums.STATUS_VALID
    image.save()
    return True


def mark_image_invalid(s3_uuid, error_message):
    try:
        image = LayerImage.objects.get(s3_uuid=s3_uuid)
    except LayerImage.DoesNotExist:
        return False

    image.status = enums.STATUS_INVALID
    image.error = error_message
    image.save()
    layer_id = image.layer_id
    return update_layer_status(layer_id, enums.STATUS_VALIDATED,
                               ERROR_MESSAGE_LAYER_IMAGE_INVALID)


def update_layer_status(layer_id, layer_status, error_message=None):
    try:
        layer = Layer.objects.get(id=layer_id)
    except Layer.DoesNotExist:
        return False

    layer.status_updated_at = datetime.now()
    update_status_column_by_enum(layer, layer_status, error_message)
    layer.save()
    return True


def get_layer_id_from_uuid(s3_uuid):
    try:
        image = LayerImage.objects.get(s3_uuid=s3_uuid)
        return image.layer_id
    except LayerImage.DoesNotExist:
        return None


def update_status_column_by_enum(layer, status, error_message=None):
    value = datetime.now()
    if status == enums.STATUS_CREATED:
        layer.status_created = value
        layer.status_created_error = error_message
    elif status == enums.STATUS_UPLOADED:
        layer.status_uploaded = value
        layer.status_uploaded = error_message
    elif status == enums.STATUS_VALIDATED:
        layer.status_validated = value
        layer.status_validated_error = error_message
    elif status == enums.STATUS_THUMBNAILED:
        layer.status_thumbnailed = value
        layer.status_thumbnailed_error = error_message
    elif status == enums.STATUS_PROCESSING:
        layer.status_handed_off = value
        layer.status_handed_off_error = error_message
    elif status == enums.STATUS_CHUNKING:
        layer.status_worker_built = value
        layer.status_worker_built_error = error_message
    elif status == enums.STATUS_CHUNKED:
        layer.status_chunked = value
        layer.status_chunked_error = error_message
    elif status == enums.STATUS_MOSAICKING:
        layer.status_mosaicking = value
        layer.status_mosaicking_error = error_message
    elif status == enums.STATUS_COMPLETED:
        layer.status_completed = value
    elif error_message is not None:
        layer.status_failed = value
    elif status == enums.STATUS_FAILED:
        layer.status_failed_error = error_message
