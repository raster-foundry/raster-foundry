# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0008_images_add_uuid_and_filename'),
    ]

    operations = [
        migrations.AlterField(
            model_name='layer',
            name='area_unit',
            field=models.CharField(default='sq. km', max_length=18, blank=True, choices=[('sq. mi', 'sq. mi'), ('sq. km', 'sq. km')]),
        ),
        migrations.AlterField(
            model_name='layer',
            name='projection',
            field=models.CharField(default='4326', help_text='Source Projection', max_length=8, blank=True, choices=[('4326', 'WGS 84')]),
        ),
        migrations.AlterField(
            model_name='layer',
            name='resampling',
            field=models.CharField(default='bilinear', max_length=12, blank=True, choices=[('bilinear', 'Bilinear'), ('cubic', 'Cubic')]),
        ),
        migrations.AlterField(
            model_name='layer',
            name='srid',
            field=models.CharField(default='4326', help_text='Source SRS', max_length=8, blank=True, choices=[('4326', 'EPSG:4326')]),
        ),
        migrations.AlterField(
            model_name='layer',
            name='tile_format',
            field=models.CharField(default='png24', max_length=8, blank=True, choices=[('jpeg', 'JPEG'), ('png8', 'PNG 8-bit'), ('png24', 'PNG 24-bit')]),
        ),
        migrations.AlterField(
            model_name='layer',
            name='tile_origin',
            field=models.CharField(default='topleft', help_text='Tiling Scheme', max_length=12, blank=True, choices=[('topleft', 'OGC / WMTS / OpenStreetMap / Google XYZ (top-left origin)'), ('bottomleft', 'OSGEO TMS (bottom-left origin)')]),
        ),
        migrations.AlterField(
            model_name='layer',
            name='tile_srid',
            field=models.CharField(default='4326', help_text='Tile SRS', max_length=8, blank=True, choices=[('4326', 'EPSG:4326')]),
        ),
        migrations.AlterField(
            model_name='layermeta',
            name='layer',
            field=models.ForeignKey(related_name='layer_metas', to='core.Layer'),
        ),
    ]
