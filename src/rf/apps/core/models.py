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

    status = CharField(
        blank=True,
        max_length=12,
        choices=enums.LAYER_STATUS_CHOICES,
        default=enums.STATUS_CREATED,
        help_text='Processing workflow status of the layer',
    )

    description = TextField(blank=True)
    organization = CharField(max_length=255, blank=True, default='')

    is_public = BooleanField(default=False)

    capture_start = DateField()
    capture_end = DateField()

    area = FloatField(default=0)
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

    created_at = DateTimeField(auto_now_add=True)
    updated_at = DateTimeField(auto_now=True)
    deleted_at = DateTimeField(null=True, blank=True)
    status_updated_at = DateTimeField(default=datetime.now)

    error = CharField(
        blank=True,
        max_length=255,
        help_text='Error that occured while processing the layer.',
    )
    thumb_small_key = CharField(max_length=255, blank=True, default='',
                                help_text='S3 key for small thumbnail')
    thumb_large_key = CharField(max_length=255, blank=True, default='',
                                help_text='S3 key for large thumbnail')

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

        return {
            'id': self.id,
            'name': self.name,
            'slug': self.slug,
            'status': self.status,
            'description': self.description,
            'organization': self.organization,
            'is_public': self.is_public,
            'capture_start': self.capture_start.isoformat(),
            'capture_end': self.capture_end.isoformat(),
            'area': self.area,
            'area_unit': self.area_unit,
            'projection': self.projection,
            'srid': self.srid,
            'tile_srid': self.tile_srid,
            'tile_format': self.tile_format,
            'tile_origin': self.tile_origin,
            'resampling': self.resampling,
            'transparency': self.transparency,
            'status': self.status,
            'created_at': self.created_at.isoformat(),
            'updated_at': self.updated_at.isoformat(),
            'status_updated_at': self.created_at.isoformat(),
            'error': self.error,

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

            # TODO: Replace with actual tiles URL.
            'tile_url': 'https://s3.amazonaws.com/raster-foundry-tiles/test_tiles/{z}/{x}/{y}.png',  # NOQA
        }

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

    def get_dismiss_url(self):
        return reverse('layer_dismiss')

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
    status = CharField(
        blank=True,
        max_length=12,
        choices=enums.LAYER_IMAGE_STATUS_CHOICES,
        default=enums.STATUS_CREATED,
        help_text='Image processing workflow status of the image',
    )
    error = CharField(
        blank=True,
        max_length=255,
        help_text='Error that occured while processing the file.',
    )

    def get_s3_key(self):
        return '%d-%s.%s' % (self.layer.user.id,
                             self.s3_uuid,
                             self.file_extension)

    def to_json(self):
        return {
            'id': self.id,
            'thumb_small': generate_thumb_url(self.thumb_small_key),
            'thumb_large': generate_thumb_url(self.thumb_large_key),
            'meta_json': self.meta_json,
            'file_name': self.file_name,
            's3_uuid': str(self.s3_uuid),
            'file_extension': self.file_extension,
            'bucket_name': self.bucket_name,
            'error': self.error,
        }


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
