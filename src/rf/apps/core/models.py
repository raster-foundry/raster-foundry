# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from datetime import datetime
import uuid

import boto3
from django.conf import settings
from django.contrib.gis.db.models import (Model, ForeignKey,
                                          CharField, TextField,
                                          DateField, DateTimeField,
                                          IntegerField, FloatField,
                                          URLField, SlugField,
                                          BooleanField, UUIDField)
from django.contrib.auth.models import User
from django.core.urlresolvers import reverse
from django.template.defaultfilters import slugify

from apps.core import enums


def generate_thumb_url(thumb_key):
    """
    Generate a signed URL for a thumbnail associated with user_id and uuid.
    """
    if thumb_key:
        s3_client = boto3.client('s3')
        presigned_url = s3_client.generate_presigned_url(
            'get_object',
            Params={'Bucket': settings.AWS_BUCKET_NAME, 'Key': thumb_key})
        return presigned_url
    else:
        return ''


class Layer(Model):
    """
    Represents a single Image Layer which may contain one or more
    geospatial images.
    """
    class Meta:
        unique_together = ('user', 'name')

    user = ForeignKey(User)
    name = CharField(max_length=255)
    slug = SlugField(max_length=255, blank=True)

    status_created = DateTimeField(auto_now_add=True)
    status_validate_start = DateTimeField(null=True, blank=True)
    status_validate_end = DateTimeField(null=True, blank=True)
    status_thumbnail_start = DateTimeField(null=True, blank=True)
    status_thumbnail_end = DateTimeField(null=True, blank=True)
    status_create_cluster_start = DateTimeField(null=True, blank=True)
    status_create_cluster_end = DateTimeField(null=True, blank=True)
    status_chunk_start = DateTimeField(null=True, blank=True)
    status_chunk_end = DateTimeField(null=True, blank=True)
    status_mosaic_start = DateTimeField(null=True, blank=True)
    status_mosaic_end = DateTimeField(null=True, blank=True)
    status_failed = DateTimeField(null=True, blank=True)
    status_completed = DateTimeField(null=True, blank=True)
    status_heartbeat = DateTimeField(null=True, blank=True)

    status_validate_error = CharField(max_length=255, blank=True, null=True)
    status_thumbnail_error = CharField(max_length=255, blank=True, null=True)
    status_create_cluster_error = CharField(max_length=255, blank=True,
                                            null=True)
    status_chunk_error = CharField(max_length=255, blank=True, null=True)
    status_mosaic_error = CharField(max_length=255, blank=True, null=True)
    status_failed_error = CharField(max_length=255, blank=True, null=True)

    description = TextField(blank=True)
    organization = CharField(max_length=255, blank=True, null=True)

    is_public = BooleanField(default=False)

    capture_start = DateField(blank=True, null=True)
    capture_end = DateField(blank=True, null=True)

    min_x = FloatField(default=None, blank=True, null=True)
    max_x = FloatField(default=None, blank=True, null=True)
    min_y = FloatField(default=None, blank=True, null=True)
    max_y = FloatField(default=None, blank=True, null=True)

    area = FloatField(default=0, blank=True, null=True)
    area_unit = CharField(
        blank=True,
        max_length=18,
        choices=enums.AREA_UNIT_CHOICES,
        default=enums.SQ_KM,
    )
    projection = CharField(
        blank=True,
        max_length=18,
        choices=enums.PROJECTION_CHOICES,
        default=enums.WGS84,
        help_text='Source Projection',
    )
    srid = CharField(
        blank=True,
        max_length=18,
        choices=enums.SRID_CHOICES,
        default=enums.WGS84,
        help_text='Source SRS',
    )

    tile_srid = CharField(
        blank=True,
        max_length=18,
        choices=enums.SRID_CHOICES,
        default=enums.WGS84,
        help_text='Tile SRS'
    )
    tile_format = CharField(
        blank=True,
        max_length=18,
        choices=enums.TILE_FORMAT_CHOICES,
        default=enums.OVER_PNG32,
    )
    tile_origin = CharField(
        blank=True,
        max_length=18,
        choices=enums.TILE_ORIGIN_CHOICES,
        default=enums.TOPLEFT,
        help_text='Tiling Scheme',
    )
    resampling = CharField(
        blank=True,
        max_length=18,
        choices=enums.TILE_RESAMPLING_CHOICES,
        default=enums.BILINEAR,
    )
    transparency = CharField(
        blank=True,
        max_length=18,
        help_text='Hexadecimal (Ex. #00FF00)',
    )

    dismissed = BooleanField(blank=True, default=False)

    created_at = DateTimeField(auto_now_add=True)
    updated_at = DateTimeField(auto_now=True)
    deleted_at = DateTimeField(null=True, blank=True)
    status_updated_at = DateTimeField(default=datetime.now)

    thumb_small_key = CharField(max_length=255, blank=True, default='',
                                help_text='S3 key for small thumbnail')
    thumb_large_key = CharField(max_length=255, blank=True, default='',
                                help_text='S3 key for large thumbnail')

    def has_copy_images(self):
        for image in self.layer_images.all():
            if image.is_copy_image():
                return True
        return False

    def save(self, *args, **kwargs):
        self.slug = slugify(self.name)
        super(Layer, self).save(*args, **kwargs)

    def to_json(self):
        """
        Return JSON serializable model data.

        Note: Prefetch all related foreign key relationships before
        calling this method for optimal performance.
        """
        tags = [m.to_json() for m in self.layer_tags.all()]
        images = [m.to_json() for m in self.layer_images.all()]

        capture_start = self.capture_start.isoformat() \
            if self.capture_start else None
        capture_end = self.capture_end.isoformat() \
            if self.capture_end else None

        return {
            'id': self.id,
            'name': self.name,
            'slug': self.slug,
            'description': self.description,
            'organization': self.organization,
            'is_public': self.is_public,
            'capture_start': capture_start,
            'capture_end': capture_end,
            'area': self.area,
            'area_unit': self.area_unit,
            'min_x': self.min_x,
            'max_x': self.max_x,
            'min_y': self.min_y,
            'max_y': self.max_y,
            'projection': self.projection,
            'srid': self.srid,
            'tile_srid': self.tile_srid,
            'tile_format': self.tile_format,
            'tile_origin': self.tile_origin,
            'resampling': self.resampling,
            'transparency': self.transparency,

            'status_created': self.status_created is not None,
            'status_validate_start': self.status_validate_start is not None,
            'status_validate_end': self.status_validate_end is not None,
            'status_thumbnail_start': self.status_thumbnail_start is not None,
            'status_thumbnail_end': self.status_thumbnail_end is not None,
            'status_create_cluster_start': (self.status_create_cluster_start
                                            is not None),
            'status_create_cluster_end': (self.status_create_cluster_end
                                          is not None),
            'status_chunk_start': self.status_chunk_start is not None,
            'status_chunk_end': self.status_chunk_end is not None,
            'status_mosaic_start': self.status_mosaic_start is not None,
            'status_mosaic_end': self.status_mosaic_end is not None,
            'status_failed': self.status_failed is not None,
            'status_completed': self.status_completed is not None,
            'status_heartbeat': self.status_completed is not None,

            'status_validate_error': self.status_validate_error,
            'status_thumbnail_error': self.status_thumbnail_error,
            'status_create_cluster_error': self.status_create_cluster_error,
            'status_chunk_error': self.status_chunk_error,
            'status_mosaic_error': self.status_mosaic_error,
            'status_failed_error': self.status_failed_error,

            'created_at': self.created_at.isoformat(),
            'updated_at': self.updated_at.isoformat(),
            'status_updated_at': self.created_at.isoformat(),

            'thumb_small': generate_thumb_url(self.thumb_small_key),
            'thumb_large': generate_thumb_url(self.thumb_large_key),

            # Foreign key fields
            'tags': tags,
            'images': images,
            'username': self.user.username,

            # Generated fields
            'url': self.get_absolute_url(),
            'meta_url': self.get_meta_url(),
            'favorite_url': self.get_favorite_url(),
            'dismiss_url': self.get_dismiss_url(),
            'retry_url': self.get_retry_url(),
            'tile_url': self.get_tile_url(),
        }

    def retry_possible(self):
        """
        Returns true if it is possible to retry processing a layer.
        """
        return self.status_failed is not None

    def reset(self):
        """
        Resets fields to prepare for a retry.
        """
        self.status_validate_start = None
        self.status_validate_end = None
        self.status_thumbnail_start = None
        self.status_thumbnail_end = None
        self.status_create_cluster_start = None
        self.status_create_cluster_end = None
        self.status_chunk_start = None
        self.status_chunk_end = None
        self.status_mosaic_start = None
        self.status_mosaic_end = None
        self.status_failed = None
        self.status_completed = None
        self.status_heartbeat = None

        self.status_validate_error = None
        self.status_thumbnail_error = None
        self.status_create_cluster_error = None
        self.status_chunk_error = None
        self.status_mosaic_error = None
        self.status_failed_error = None
        self.save()

        for image in self.layer_images.all():
            image.reset()

    def get_absolute_url(self):
        kwargs = {
            'layer_id': self.id,
            'username': self.user.username,
        }
        return reverse('layer_detail', kwargs=kwargs)

    def get_meta_url(self):
        kwargs = {
            'layer_id': self.id,
            'username': self.user.username,
        }
        return reverse('layer_meta', kwargs=kwargs)

    def get_favorite_url(self):
        kwargs = {
            'layer_id': self.id,
        }
        return reverse('create_or_destroy_favorite', kwargs=kwargs)

    def get_retry_url(self):
        return reverse('layer_retry')

    def get_dismiss_url(self):
        return reverse('layer_dismiss')

    def get_tile_url(self):
        url = 'https://s3.amazonaws.com/%s/%d/{z}/{x}/{y}.png'
        return url % (settings.AWS_TILES_BUCKET, self.id)

    def get_tile_bucket_path(self):
        return 's3://{}/{}'.format(settings.AWS_TILES_BUCKET, self.id)

    def process_failed_heartbeat(self):
        self.status_heartbeat = datetime.now()
        self.save()

    def update_status_start(self, status):
        value = datetime.now()
        if status == enums.STATUS_VALIDATE:
            self.status_validate_start = (value if self.status_validate_start
                                          is None else
                                          self.status_validate_start)
        elif status == enums.STATUS_THUMBNAIL:
            self.status_thumbnail_start = (value if self.status_thumbnail_start
                                           is None else
                                           self.status_thumbnail_start)
        elif status == enums.STATUS_CREATE_CLUSTER:
            self.status_create_cluster_start = \
                (value if self.status_create_cluster_start is None else
                 self.status_create_cluster_start)
        elif status == enums.STATUS_CHUNK:
            self.status_chunk_start = (value if self.status_chunk_start
                                       is None else self.status_chunk_start)
        elif status == enums.STATUS_MOSAIC:
            self.status_mosaic_start = (value if self.status_mosaic_start
                                        is None else self.status_mosaic_start)

    def update_status_end(self, status, error_message=None):
        value = datetime.now()
        if status == enums.STATUS_VALIDATE:
            self.status_validate_end = value
            self.status_validate_error = error_message
        elif status == enums.STATUS_THUMBNAIL:
            self.status_thumbnail_end = value
            self.status_thumbnail_error = error_message
        elif status == enums.STATUS_CREATE_CLUSTER:
            self.status_create_cluster_end = value
            self.status_create_cluster_error = error_message
        elif status == enums.STATUS_CHUNK:
            self.status_chunk_end = value
            self.status_chunk_error = error_message
        elif status == enums.STATUS_MOSAIC:
            self.status_mosaic_end = value
            self.status_mosaic_error = error_message
        elif status == enums.STATUS_COMPLETED:
            if error_message is not None:
                raise StatusMismatchError(
                    'Completed status does not accept errors.')
            self.status_completed = value
            # To be safe, unset the failed status and error.
            self.status_failed = None
            self.status_failed_error = None
        elif status == enums.STATUS_FAILED:
            if self.status_completed is not None:
                raise StatusMismatchError(
                    'Cannot mark completed layer as failed.')
            self.status_failed_error = error_message
        # If we had any error message mark the generic failed field.
        if error_message is not None:
            if self.status_completed is not None:
                raise StatusMismatchError(
                    'Cannot set errors on completed layer.')
            self.status_failed = value

    def mark_failed(self):
        value = datetime.now()
        self.status_failed = value

    def set_bounds(self, bounds):
        self.min_x = bounds[0]
        self.max_x = bounds[1]
        self.min_y = bounds[2]
        self.max_y = bounds[3]
        self.save(update_fields=['min_x', 'max_x', 'min_y', 'max_y'])

    def __unicode__(self):
        return '{0} -> {1}'.format(self.user.username, self.name)


class LayerTag(Model):
    """
    Arbitrary value used to describe a layer which may be used
    to discover similar layers with tags in common.
    """
    layer = ForeignKey(Layer, related_name='layer_tags')
    name = CharField(max_length=24)

    def to_json(self):
        return self.name


class LayerImage(Model):
    """
    Geospatial image uploaded by the user.

    `meta_json` may be populated during geoprocessing and should contain
    a serialized JSON blob of image metadata.
    This blob should be in the form of key value pairs (object literal)
    and will be displayed as-is.
    """
    layer = ForeignKey(Layer, related_name='layer_images')
    priority = IntegerField(
        default=0,
        help_text='The order which images are layered (starting from 0)'
    )
    thumb_small_key = CharField(max_length=255, blank=True, default='',
                                help_text='S3 key for small thumbnail')
    thumb_large_key = CharField(max_length=255, blank=True, default='',
                                help_text='S3 key for large thumbnail')
    meta_json = TextField(
        null=True,
        blank=True,
        help_text='Serialized JSON of image metadata',
    )

    min_x = FloatField(default=None, blank=True, null=True)
    max_x = FloatField(default=None, blank=True, null=True)
    min_y = FloatField(default=None, blank=True, null=True)
    max_y = FloatField(default=None, blank=True, null=True)

    file_name = CharField(
        blank=False,
        default='',
        max_length=255,
        help_text='Filename of original file',
    )
    s3_uuid = UUIDField(
        default=uuid.uuid4,
        editable=False
    )
    file_extension = CharField(
        blank=False,
        default='',
        max_length=10,
        help_text='Extension of file'
    )
    bucket_name = CharField(
        blank=False,
        default='',
        max_length=255,
        help_text='Name of S3 bucket'
    )
    source_s3_bucket_key = CharField(
        blank=True,
        null=True,
        max_length=255,
        help_text='S3 <bucket>/<key> for source image (optional)'
    )

    status_created = DateTimeField(auto_now_add=True)
    status_transfer_start = DateTimeField(null=True, blank=True)
    status_transfer_end = DateTimeField(null=True, blank=True)
    status_validate_start = DateTimeField(null=True, blank=True)
    status_validate_end = DateTimeField(null=True, blank=True)
    status_thumbnail_start = DateTimeField(null=True, blank=True)
    status_thumbnail_end = DateTimeField(null=True, blank=True)

    status_upload_error = CharField(max_length=255, blank=True, null=True)
    status_transfer_error = CharField(max_length=255, blank=True, null=True)
    status_validate_error = CharField(max_length=255, blank=True, null=True)
    status_thumbnail_error = CharField(max_length=255, blank=True, null=True)

    def reset(self):
        """
        Resets fields to prepare for a retry.
        """
        self.status_transfer_start = None
        self.status_transfer_end = None
        self.status_validate_start = None
        self.status_validate_end = None
        self.status_thumbnail_start = None
        self.status_thumbnail_end = None

        self.status_upload_error = None
        self.status_transfer_error = None
        self.status_validate_error = None
        self.status_thumbnail_error = None

        self.save()

    def has_been_validated(self):
        return self.status_validate_end is not None

    def is_copy_image(self):
        return self.source_s3_bucket_key is not None

    def get_s3_key(self):
        return '%d-%s.%s' % (self.layer.user.id,
                             self.s3_uuid,
                             self.file_extension)

    def get_s3_uri(self):
        return 's3://{}/{}-{}.{}'.format(self.bucket_name,
                                         self.layer.user_id,
                                         self.s3_uuid,
                                         self.file_extension)

    def to_json(self):
        return {
            'id': self.id,
            'thumb_small': generate_thumb_url(self.thumb_small_key),
            'thumb_large': generate_thumb_url(self.thumb_large_key),
            'meta_json': self.meta_json,
            'min_x': self.min_x,
            'max_x': self.max_x,
            'min_y': self.min_y,
            'max_y': self.max_y,
            'file_name': self.file_name,
            's3_uuid': str(self.s3_uuid),
            'file_extension': self.file_extension,
            'bucket_name': self.bucket_name,
            'source_s3_bucket_key': self.source_s3_bucket_key,
            'status_created': self.status_created is not None,
            'status_transfer_start': self.status_transfer_start is not None,
            'status_transfer_end': self.status_transfer_end is not None,
            'status_validate_start': self.status_validate_start is not None,
            'status_validate_end': self.status_validate_end is not None,
            'status_thumbnail_start': self.status_thumbnail_start is not None,
            'status_thumbnail_end': self.status_thumbnail_end is not None,
            'status_upload_error': self.status_upload_error,
            'status_transfer_error': self.status_transfer_error,
            'status_validate_error': self.status_validate_error,
            'status_thumbnail_error': self.status_thumbnail_error,
            'source_s3_bucket_key': self.source_s3_bucket_key
        }

    def update_status_start(self, status):
        value = datetime.now()
        if status == enums.STATUS_TRANSFER:
            self.status_transfer_start = (value if self.status_transfer_start
                                          is None else
                                          self.status_transfer_start)
        elif status == enums.STATUS_VALIDATE:
            self.status_validate_start = (value if self.status_validate_start
                                          is None else
                                          self.status_validate_start)
        elif status == enums.STATUS_THUMBNAIL:
            self.status_thumbnail_start = (value if self.status_thumbnail_start
                                           is None else
                                           self.status_thumbnail_start)

    def update_status_end(self, status, error_message=None):
        value = datetime.now()
        if status == enums.STATUS_TRANSFER:
            self.status_transfer_end = value
            self.status_transfer_error = error_message
        elif status == enums.STATUS_VALIDATE:
            self.status_validate_end = value
            self.status_validate_error = error_message
        elif status == enums.STATUS_THUMBNAIL:
            self.status_thumbnail_end = value
            self.status_thumbnail_error = error_message

    def set_bounds(self, bounds):
        self.min_x = bounds[0]
        self.max_x = bounds[1]
        self.min_y = bounds[2]
        self.max_y = bounds[3]
        self.save(update_fields=['min_x', 'max_x', 'min_y', 'max_y'])


class LayerMeta(Model):
    """
    Immutable state of layer uploading & geoprocessing progress.

    To maintain an audit trail of each status change for a layer, these
    records should *not* be mutated. Instead, a new record should be created
    for each status change.

    The data in this table will primarily be maintained by the
    geoprocessing side of things.
    """
    layer = ForeignKey(Layer, related_name='layer_metas')
    state = CharField(max_length=16)
    error = TextField(null=True, blank=True)
    thumb_small = URLField(
        null=True,
        blank=True,
        help_text='80x80 pixels',
    )
    thumb_large = URLField(
        null=True,
        blank=True,
        help_text='400x150 pixels',
    )
    created_at = DateTimeField(auto_now_add=True)

    # TileJSON fields
    min_zoom = IntegerField(default=0)
    max_zoom = IntegerField(default=11)
    bounds = CharField(
        null=True,
        max_length=120,
        help_text='JSON array',
    )
    center = CharField(
        null=True,
        max_length=60,
        help_text='JSON array',
    )

    def to_json(self):
        return {
            'id': self.id,
            'state': self.state,
            'error': self.error,
            'thumb_small': self.thumb_small,
            'thumb_large': self.thumb_large,
            'created_at': self.created_at.isoformat(),
            'min_zoom': self.min_zoom,
            'max_zoom': self.max_zoom,
            'bounds': self.bounds,
            'center': self.center,
        }


class UserProfile(Model):
    """
    Additional information for user profiles.
    """
    user = ForeignKey(User)
    organization = CharField(max_length=255, blank=True, default='')


class UserFavoriteLayer(Model):
    """
    Created when a layer is "starred" and destroyed when that layer
    is "un-starred".

    Users may "star" their own layers or published layers.
    """
    user = ForeignKey(User)
    layer = ForeignKey(Layer, related_name='favorites')
    created_at = DateTimeField(auto_now_add=True)

    def __unicode__(self):
        return '{0} -> {1}'.format(self.user.username, self.layer.name)


class StatusMismatchError(ValueError):
    """
    Raised if statuses are incompatible.
    """
    pass
