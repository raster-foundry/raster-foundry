# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division


from apps.core.models import LayerImage, Layer
import apps.core.enums as enums

ERROR_MESSAGE_LAYER_IMAGE_INVALID = 'Cannot process invalid images.'
ERROR_MESSAGE_LAYER_IMAGE_TRANSFER = 'Processing aborted. Missing images.'


def mark_image_uploaded(s3_uuid):
    try:
        image = LayerImage.objects.get(s3_uuid=s3_uuid)
    except LayerImage.DoesNotExist:
        return False

    image.update_status_start(enums.STATUS_UPLOAD)
    image.update_status_end(enums.STATUS_UPLOAD)
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


def mark_layer_thumbnailed(layer_id):
    """
    If thumbnails are generated for the layer and associated
    LayerImages, then update the status of layer to reflect that.
    """
    try:
        layer = Layer.objects.get(id=layer_id)
    except Layer.DoesNotExist:
        return False

    thumbnailed_images = LayerImage.objects.filter(
        layer_id=layer_id,
        status_thumbnail_end__isnull=False,
        status_thumbnail_error__isnull=True)
    layer_images = layer.layer_images.all()

    all_images_thumbnailed = layer_images.count() == thumbnailed_images.count()
    layer_thumbnailed = layer.thumb_small_key is not None
    if all_images_thumbnailed and layer_thumbnailed:
        return mark_layer_status_end(layer_id, enums.STATUS_THUMBNAIL)

    return True


def mark_image_status_start(s3_uuid, status):
    try:
        image = LayerImage.objects.get(s3_uuid=s3_uuid)
    except LayerImage.DoesNotExist:
        return False

    image.update_status_start(status)
    image.save()

    return mark_layer_status_start(image.layer_id, status)


def mark_image_status_end(s3_uuid, status):
    try:
        image = LayerImage.objects.get(s3_uuid=s3_uuid)
    except LayerImage.DoesNotExist:
        return False

    image.update_status_end(status)
    image.save()
    return True


def mark_layer_status_start(layer_id, status):
    try:
        layer = Layer.objects.get(id=layer_id)
    except Layer.DoesNotExist:
        return False

    layer.update_status_start(status)
    layer.save()
    return True


def mark_layer_status_end(layer_id, status, error_message=None):
    try:
        layer = Layer.objects.get(id=layer_id)
    except Layer.DoesNotExist:
        return False

    layer.update_status_end(status, error_message)
    layer.save()
    return True


def mark_image_valid(s3_uuid):
    try:
        image = LayerImage.objects.get(s3_uuid=s3_uuid)
    except LayerImage.DoesNotExist:
        return False

    image.update_status_end(enums.STATUS_VALIDATE)
    image.save()
    return True


def mark_image_invalid(s3_uuid, error_message):
    try:
        image = LayerImage.objects.get(s3_uuid=s3_uuid)
    except LayerImage.DoesNotExist:
        return False

    image.update_status_end(enums.STATUS_VALIDATE, error_message)
    image.save()

    return mark_layer_status_end(image.layer_id, enums.STATUS_VALIDATE,
                                 ERROR_MESSAGE_LAYER_IMAGE_INVALID)


def mark_image_transfer_start(s3_uuid):
    try:
        image = LayerImage.objects.get(s3_uuid=s3_uuid)
    except LayerImage.DoesNotExist:
        return False

    image.update_status_start(enums.STATUS_UPLOAD)
    image.save()
    return mark_layer_status_start(image.layer_id, enums.STATUS_UPLOAD)


def mark_image_transfer_failure(s3_uuid, error_message):
    try:
        image = LayerImage.objects.get(s3_uuid=s3_uuid)
    except LayerImage.DoesNotExist:
        return False

    image.update_status_end(enums.STATUS_UPLOAD, error_message)
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
