# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.contrib import admin

from apps.core import models


class LayerTagInline(admin.TabularInline):
    extra = 0
    model = models.LayerTag


class LayerImageInline(admin.TabularInline):
    extra = 0
    model = models.LayerImage


class LayerMetaInline(admin.TabularInline):
    extra = 0
    model = models.LayerMeta


class LayerAdmin(admin.ModelAdmin):
    inlines = [LayerTagInline, LayerImageInline, LayerMetaInline]
    readonly_fields = ('created_at', 'updated_at', 'status_created')
    fieldsets = (
        (None, {
            'fields': (
                'user', 'name', 'slug',
                ('status_created'),
                ('status_upload_start', 'status_upload_end'),
                ('status_validate_start', 'status_validate_end'),
                ('status_thumbnail_start', 'status_thumbnail_end'),
                ('status_create_cluster_start', 'status_create_cluster_end'),
                ('status_chunk_start', 'status_chunk_end'),
                ('status_mosaic_start', 'status_mosaic_end'),
                ('status_failed', 'status_completed'),
                ('status_upload_error', 'status_validate_error'),
                ('status_thumbnail_error', 'status_create_cluster_error'),
                ('status_chunk_error', 'status_mosaic_error'),
                'status_failed_error',
                'description', 'is_public', 'dismissed',
                ('capture_start', 'capture_end'),
                ('area', 'area_unit'),
                'projection', 'srid',
                'created_at', 'updated_at', 'deleted_at', 'status_updated_at'
            )
        }),
        ('Tiles', {
            'fields': (
                'tile_srid', 'tile_format', 'tile_origin', 'resampling',
                'transparency'
            )
        }),
    )


admin.site.register(models.Layer, LayerAdmin)
admin.site.register(models.UserFavoriteLayer, admin.ModelAdmin)
