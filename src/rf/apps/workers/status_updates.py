# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division


from datetime import datetime

from apps.core.models import LayerImage, Layer
import apps.core.enums as enums

ERROR_MESSAGE_LAYER_IMAGE_INVALID = 'Cannot process invalid images.'
ERROR_MESSAGE_LAYER_IMAGE_TRANSFER = 'Processing aborted. Missing images.'


def mark_image_uploaded(s3_uuid):
    try:
        image = LayerImage.objects.get(s3_uuid=s3_uuid)
    except LayerImage.DoesNotExist:
        return False

    image = update_layerimage_status_start(image, enums.STATUS_UPLOAD)
    image = update_layerimage_status_end(image, enums.STATUS_UPLOAD)
    image.save()

    # It is okay to repeat this. It will not overwrite existing timestamps.
    if not mark_layer_status_start(image.layer_id, enums.STATUS_UPLOAD):
        return False

    layer_images = LayerImage.objects.filter(layer_id=image.layer_id)
    uploaded_layer_images = LayerImage.objects.filter(
        layer_id=image.layer_id,
        status_upload_end__isnull=False,
        status_upload_error__isnull=True)

    # If all images are uploaded, then mark layer upload as complete.
    if len(layer_images) == len(uploaded_layer_images) and \
            len(layer_images) > 0:
        return mark_layer_status_end(image.layer_id, enums.STATUS_UPLOAD)

    return True


def mark_image_status_start(s3_uuid, status):
    try:
        image = LayerImage.objects.get(s3_uuid=s3_uuid)
    except LayerImage.DoesNotExist:
        return False

    image = update_layerimage_status_start(image, status)
    image.save()

    return mark_layer_status_start(image.layer_id, status)


def mark_image_status_end(s3_uuid, status):
    try:
        image = LayerImage.objects.get(s3_uuid=s3_uuid)
    except LayerImage.DoesNotExist:
        return False

    image = update_layerimage_status_end(image, status)
    image.save()
    return True


def mark_layer_status_start(layer_id, status):
    try:
        layer = Layer.objects.get(id=layer_id)
    except Layer.DoesNotExist:
        return False

    layer = update_layer_status_start(layer, status)
    layer.save()
    return True


def mark_layer_status_end(layer_id, status, error_message=None):
    try:
        layer = Layer.objects.get(id=layer_id)
    except Layer.DoesNotExist:
        return False

    layer = update_layer_status_end(layer, status, error_message)
    layer.save()
    return True


def mark_image_valid(s3_uuid):
    try:
        image = LayerImage.objects.get(s3_uuid=s3_uuid)
    except LayerImage.DoesNotExist:
        return False

    image = update_layerimage_status_end(image, enums.STATUS_VALIDATE)
    image.save()
    return True


def mark_image_invalid(s3_uuid, error_message):
    try:
        image = LayerImage.objects.get(s3_uuid=s3_uuid)
    except LayerImage.DoesNotExist:
        return False

    image = update_layerimage_status_end(image, enums.STATUS_VALIDATE,
                                         error_message)
    image.save()

    return update_layer_status_end(image.layer_id, enums.STATUS_VALIDATE,
                                   ERROR_MESSAGE_LAYER_IMAGE_INVALID)


def mark_image_transfer_start(s3_uuid):
    try:
        image = LayerImage.objects.get(s3_uuid=s3_uuid)
    except LayerImage.DoesNotExist:
        return False

    image = update_layerimage_status_start(image, enums.STATUS_UPLOAD)
    image.save()
    return mark_layer_status_start(image.layer_id, enums.STATUS_UPLOAD)


def mark_image_transfer_failure(s3_uuid, error_message):
    try:
        image = LayerImage.objects.get(s3_uuid=s3_uuid)
    except LayerImage.DoesNotExist:
        return False

    image = update_layerimage_status_end(image,
                                         enums.STATUS_UPLOAD,
                                         error_message)
    image.save()
    return mark_layer_status_end(image.layer_id,
                                 enums.STATUS_UPLOAD,
                                 ERROR_MESSAGE_LAYER_IMAGE_TRANSFER)


def get_layer_id_from_uuid(s3_uuid):
    try:
        image = LayerImage.objects.get(s3_uuid=s3_uuid)
        return image.layer_id
    except LayerImage.DoesNotExist:
        return None


def update_layer_status_start(layer, status):
    value = datetime.now()
    if status == enums.STATUS_UPLOAD:
        layer.status_upload_start = (value if layer.status_upload_start
                                     is None else layer.status_upload_start)

    elif status == enums.STATUS_VALIDATE:
        layer.status_validate_start = (value if layer.status_validate_start
                                       is None else
                                       layer.status_validate_start)

    elif status == enums.STATUS_THUMBNAIL:
        layer.status_thumbnail_start = (value if layer.status_thumbnail_start
                                        is None else
                                        layer.status_thumbnail_start)

    elif status == enums.STATUS_CREATE_CLUSTER:
        layer.status_create_cluster_start = (value if
                                             layer.status_create_cluster_start
                                             is None else
                                             layer.status_create_cluster_start)

    elif status == enums.STATUS_CHUNK:
        layer.status_chunk_start = (value if layer.status_chunk_start
                                    is None else layer.status_chunk_start)

    elif status == enums.STATUS_MOSAIC:
        layer.status_mosaic_start = (value if layer.status_mosaic_start
                                     is None else layer.status_mosaic_start)

    return layer


def update_layer_status_end(layer, status, error_message=None):
    value = datetime.now()
    if status == enums.STATUS_UPLOAD:
        layer.status_upload_end = value
        layer.status_upload_error = error_message
    elif status == enums.STATUS_VALIDATE:
        layer.status_validate_end = value
    elif status == enums.STATUS_THUMBNAIL:
        layer.status_thumbnail_end = value
        layer.status_thumbnail_error = error_message
    elif status == enums.STATUS_CREATE_CLUSTER:
        layer.status_create_cluster_end = value
        layer.status_create_cluster_error = error_message
    elif status == enums.STATUS_CHUNK:
        layer.status_chunk_end = value
        layer.status_chunk_error = error_message
    elif status == enums.STATUS_MOSAIC:
        layer.status_mosaic_end = value
        layer.status_mosaic_error = error_message
    elif status == enums.STATUS_COMPLETED:
        layer.status_completed = value
    elif status == enums.STATUS_FAILED:
        layer.status_failed_error = error_message

    # If we had any error message mark the generic failed field.
    if error_message is not None:
        layer.status_failed = value

    return layer


def update_layerimage_status_start(image, status):
    value = datetime.now()
    if status == enums.STATUS_UPLOAD:
        image.status_upload_start = value
    elif status == enums.STATUS_VALIDATE:
        image.status_validate_start = value
    elif status == enums.STATUS_THUMBNAIL:
        image.status_thumbnail_start = value
    return image


def update_layerimage_status_end(image, status, error_message=None):
    value = datetime.now()
    if status == enums.STATUS_UPLOAD:
        image.status_upload_end = value
        image.status_upload_error = error_message
    elif status == enums.STATUS_VALIDATE:
        image.status_validate_end = value
        image.status_validate_error = error_message
    elif status == enums.STATUS_THUMBNAIL:
        image.status_thumbnail_end = value
        image.status_thumbnail_error = error_message
    return image
