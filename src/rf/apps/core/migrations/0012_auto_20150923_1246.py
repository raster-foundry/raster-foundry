# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0011_auto_20150923_1057'),
    ]

    operations = [
        migrations.AlterField(
            model_name='layer',
            name='projection',
            field=models.CharField(default='4326', help_text='Source Projection', max_length=18, blank=True, choices=[('4326', 'WGS 84')]),
        ),
        migrations.AlterField(
            model_name='layer',
            name='srid',
            field=models.CharField(default='4326', help_text='Source SRS', max_length=18, blank=True, choices=[('mercator', 'Web Mercator'), ('utm', 'UTM'), ('4326', 'EPSG:4326'), ('epsg', 'EPSG/ESRI offline database')]),
        ),
        migrations.AlterField(
            model_name='layer',
            name='tile_srid',
            field=models.CharField(default='4326', help_text='Tile SRS', max_length=18, blank=True, choices=[('mercator', 'Web Mercator'), ('utm', 'UTM'), ('4326', 'EPSG:4326'), ('epsg', 'EPSG/ESRI offline database')]),
        ),
    ]
