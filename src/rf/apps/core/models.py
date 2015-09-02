# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.contrib.gis.db.models import (Model, ForeignKey,
                                          CharField, TextField,
                                          DateField, DateTimeField,
                                          IntegerField, FloatField,
                                          URLField, SlugField,
                                          BooleanField, UUIDField)
from django.contrib.auth.models import User
from django.template.defaultfilters import slugify

from apps.core import enums
import uuid


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
    description = TextField(blank=True)
    organization = CharField(max_length=255, blank=True, default='')

    is_public = BooleanField(default=False)

    capture_start = DateField()
    capture_end = DateField()

    area = FloatField(default=0)
    area_unit = CharField(
        max_length=18,
        choices=enums.AREA_UNIT_CHOICES,
        default=enums.SQ_KM,
    )
    projection = CharField(
        max_length=8,
        choices=enums.PROJECTION_CHOICES,
        default=enums.WGS84,
        help_text='Source Projection',
    )
    srid = CharField(
        max_length=8,
        choices=enums.SRID_CHOICES,
        default=enums.WGS84,
        help_text='Source SRS',
    )

    tile_srid = CharField(
        max_length=8,
        choices=enums.SRID_CHOICES,
        default=enums.WGS84,
        help_text='Tile SRS'
    )
    tile_format = CharField(
        max_length=8,
        choices=enums.TILE_FORMAT_CHOICES,
        default=enums.PNG24,
    )
    tile_origin = CharField(
        max_length=12,
        choices=enums.TILE_ORIGIN_CHOICES,
        default=enums.TOPLEFT,
        help_text='Tiling Scheme',
    )
    resampling = CharField(
        max_length=12,
        choices=enums.TILE_RESAMPLING_CHOICES,
        default=enums.BILINEAR,
    )
    transparency = CharField(
        blank=True,
        max_length=8,
        help_text='Hexadecimal (Ex. #00FF00)',
    )

    created_at = DateTimeField(auto_now_add=True)
    updated_at = DateTimeField(auto_now=True)
    deleted_at = DateTimeField(null=True, blank=True)

    def save(self, *args, **kwargs):
        self.slug = slugify(self.name)
        super(Layer, self).save(*args, **kwargs)

    def __unicode__(self):
        return self.name


class LayerTag(Model):
    """
    Arbitrary value used to describe a layer which may be used
    to discover similar layers with tags in common.
    """
    layer = ForeignKey(Layer, related_name='layer_tags')
    name = CharField(max_length=24)


class LayerImage(Model):
    """
    Geospatial image uploaded by the user.

    `meta_json` may be populated during geoprocessing and should contain
    a serialized JSON blob of image metadata.
    This blob should be in the form of key value pairs (object literal)
    and will be displayed as-is.
    """
    layer = ForeignKey(Layer, related_name='layer_images')
    source_uri = URLField(max_length=2000)
    priority = IntegerField(
        default=0,
        help_text='The order which images are layered (starting from 0)'
    )
    thumb_small = URLField(
        null=True,
        blank=True,
        help_text='80x80 pixels',
    )
    thumb_large = URLField(
        null=True,
        blank=True,
        help_text='300x300 pixels',
    )
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


class LayerMeta(Model):
    """
    Immutable state of layer uploading & geoprocessing progress.

    To maintain an audit trail of each status change for a layer, these
    records should *not* be mutated. Instead, a new record should be created
    for each status change.

    The data in this table will primarily be maintained by the
    geoprocessing side of things.
    """
    layer = ForeignKey(Layer)
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
