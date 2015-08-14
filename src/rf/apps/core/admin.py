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
    readonly_fields = ('created_at', 'updated_at')
    fieldsets = (
        (None, {
            'fields': (
                'user', 'name', 'description',
                ('capture_start', 'capture_end'),
                ('area', 'area_unit'),
                'projection', 'srid',
                'created_at', 'updated_at', 'deleted_at'
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
