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
    return update_layer_status(layer_id, enums.STATUS_FAILED,
                               ERROR_MESSAGE_LAYER_IMAGE_INVALID)


def update_layer_status(layer_id, layer_status, error_message=None):
    try:
        layer = Layer.objects.get(id=layer_id)
    except Layer.DoesNotExist:
        return False

    layer.status = layer_status
    layer.status_updated_at = datetime.now()
    layer.error = error_message
    layer.save()
    return True


def get_layer_id_from_uuid(s3_uuid):
    try:
        image = LayerImage.objects.get(s3_uuid=s3_uuid)
        return image.layer_id
    except LayerImage.DoesNotExist:
        return None
