# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations
from django.conf import settings


class Migration(migrations.Migration):

    dependencies = [
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.CreateModel(
            name='Layer',
            fields=[
                ('id', models.AutoField(verbose_name='ID', serialize=False, auto_created=True, primary_key=True)),
                ('name', models.CharField(max_length=255)),
                ('description', models.TextField(blank=True)),
                ('organization', models.CharField(default='', max_length=255, blank=True)),
                ('capture_start', models.DateField()),
                ('capture_end', models.DateField()),
                ('area', models.FloatField(default=0)),
                ('area_unit', models.CharField(default='sq. km', max_length=18, choices=[('sq. mi', 'sq. mi'), ('sq. km', 'sq. km')])),
                ('projection', models.CharField(default='4326', help_text='Source Projection', max_length=8, choices=[('4326', 'WGS 84')])),
                ('srid', models.CharField(default='4326', help_text='Source SRS', max_length=8, choices=[('4326', 'EPSG:4326')])),
                ('tile_srid', models.CharField(default='4326', help_text='Tile SRS', max_length=8, choices=[('4326', 'EPSG:4326')])),
                ('tile_format', models.CharField(default='png24', max_length=8, choices=[('jpeg', 'JPEG'), ('png8', 'PNG 8-bit'), ('png24', 'PNG 24-bit')])),
                ('tile_origin', models.CharField(default='topleft', help_text='Tiling Scheme', max_length=12, choices=[('topleft', 'OGC / WMTS / OpenStreetMap / Google XYZ (top-left origin)'), ('bottomleft', 'OSGEO TMS (bottom-left origin)')])),
                ('resampling', models.CharField(default='bilinear', max_length=12, choices=[('bilinear', 'Bilinear'), ('cubic', 'Cubic')])),
                ('transparency', models.CharField(help_text='Hexadecimal (Ex. #00FF00)', max_length=8, blank=True)),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('updated_at', models.DateTimeField(auto_now=True)),
                ('deleted_at', models.DateTimeField(null=True, blank=True)),
                ('user', models.ForeignKey(to=settings.AUTH_USER_MODEL)),
            ],
        ),
        migrations.CreateModel(
            name='LayerImage',
            fields=[
                ('id', models.AutoField(verbose_name='ID', serialize=False, auto_created=True, primary_key=True)),
                ('source_uri', models.URLField(max_length=2000)),
                ('priority', models.IntegerField(default=0, help_text='The order which images are layered (starting from 0)')),
                ('thumb_small', models.URLField(help_text='80x80 pixels', null=True, blank=True)),
                ('thumb_large', models.URLField(help_text='300x300 pixels', null=True, blank=True)),
                ('meta_json', models.TextField(help_text='Serialized JSON of image metadata', null=True, blank=True)),
                ('layer', models.ForeignKey(to='core.Layer')),
            ],
        ),
        migrations.CreateModel(
            name='LayerMeta',
            fields=[
                ('id', models.AutoField(verbose_name='ID', serialize=False, auto_created=True, primary_key=True)),
                ('state', models.CharField(max_length=16)),
                ('error', models.TextField(null=True, blank=True)),
                ('thumb_small', models.URLField(help_text='80x80 pixels', null=True, blank=True)),
                ('thumb_large', models.URLField(help_text='400x150 pixels', null=True, blank=True)),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('layer', models.ForeignKey(to='core.Layer')),
            ],
        ),
        migrations.CreateModel(
            name='LayerTag',
            fields=[
                ('id', models.AutoField(verbose_name='ID', serialize=False, auto_created=True, primary_key=True)),
                ('name', models.CharField(max_length=24)),
                ('layer', models.ForeignKey(to='core.Layer')),
            ],
        ),
        migrations.CreateModel(
            name='UserFavoriteLayer',
            fields=[
                ('id', models.AutoField(verbose_name='ID', serialize=False, auto_created=True, primary_key=True)),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('layer', models.ForeignKey(to='core.Layer')),
                ('user', models.ForeignKey(to=settings.AUTH_USER_MODEL)),
            ],
        ),
        migrations.CreateModel(
            name='UserProfile',
            fields=[
                ('id', models.AutoField(verbose_name='ID', serialize=False, auto_created=True, primary_key=True)),
                ('organization', models.CharField(default='', max_length=255, blank=True)),
                ('user', models.ForeignKey(to=settings.AUTH_USER_MODEL)),
            ],
        ),
    ]
