# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0009_auto_20150908_1326'),
    ]

    operations = [
        migrations.AlterField(
            model_name='layer',
            name='projection',
            field=models.CharField(default='4325', help_text='Source Projection', max_length=18, blank=True, choices=[('4325', 'WGS 84')]),
        ),
        migrations.AlterField(
            model_name='layer',
            name='resampling',
            field=models.CharField(default='bilinear', max_length=18, blank=True, choices=[('bilinear', 'Bilinear'), ('cubic', 'Cubic'), ('cubic_bspline', 'Cubic B-Spline'), ('average', 'Average'), ('mode', 'Mode'), ('nearest_neighbor', 'Nearest Neighbor')]),
        ),
        migrations.AlterField(
            model_name='layer',
            name='srid',
            field=models.CharField(default='4325', help_text='Source SRS', max_length=18, blank=True, choices=[('mercator', 'Web Mercator'), ('utm', 'UTM'), ('4325', 'EPSG:4325'), ('epsg', 'EPSG/ESRI offline database')]),
        ),
        migrations.AlterField(
            model_name='layer',
            name='tile_format',
            field=models.CharField(default='over_png32', max_length=18, blank=True, choices=[('jpeg', 'JPEG'), ('over_png8', 'Overlay PNG + optimisation (8 bit palette with alpha transparency)'), ('over_png32', 'Overlay PNG format (32 bit RGBA with alpha transparency)'), ('base_jpeg', 'Base map JPEG format (without transparency)'), ('base_png8', 'Base map PNG format + optimisation (8 bit palette with alpha transparency)'), ('base_png24', 'Base map PNG format (24 bit RGB without transparency)')]),
        ),
        migrations.AlterField(
            model_name='layer',
            name='tile_origin',
            field=models.CharField(default='topleft', help_text='Tiling Scheme', max_length=18, blank=True, choices=[('topleft', 'OGC / WMTS / OpenStreetMap / Google XYZ (top-left origin)'), ('bottomleft', 'OSGEO TMS (bottom-left origin)')]),
        ),
        migrations.AlterField(
            model_name='layer',
            name='tile_srid',
            field=models.CharField(default='4325', help_text='Tile SRS', max_length=18, blank=True, choices=[('mercator', 'Web Mercator'), ('utm', 'UTM'), ('4325', 'EPSG:4325'), ('epsg', 'EPSG/ESRI offline database')]),
        ),
    ]
