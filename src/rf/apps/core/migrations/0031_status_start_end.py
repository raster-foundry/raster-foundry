# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models
import datetime
from django.utils.timezone import utc


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0030_processing_status'),
    ]

    operations = [
        migrations.RemoveField(
            model_name='layer',
            name='error',
        ),
        migrations.RemoveField(
            model_name='layer',
            name='status',
        ),
        migrations.RemoveField(
            model_name='layerimage',
            name='error',
        ),
        migrations.RemoveField(
            model_name='layerimage',
            name='status',
        ),
        migrations.AddField(
            model_name='layer',
            name='status_chunk_end',
            field=models.DateTimeField(null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layer',
            name='status_chunk_error',
            field=models.CharField(max_length=255, null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layer',
            name='status_chunk_start',
            field=models.DateTimeField(null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layer',
            name='status_completed',
            field=models.DateTimeField(null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layer',
            name='status_create_cluster_end',
            field=models.DateTimeField(null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layer',
            name='status_create_cluster_error',
            field=models.CharField(max_length=255, null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layer',
            name='status_create_cluster_start',
            field=models.DateTimeField(null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layer',
            name='status_created',
            field=models.DateTimeField(default=datetime.datetime(2015, 10, 27, 21, 47, 47, 939776, tzinfo=utc), auto_now_add=True),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='layer',
            name='status_failed',
            field=models.DateTimeField(null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layer',
            name='status_failed_error',
            field=models.CharField(max_length=255, null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layer',
            name='status_mosaic_end',
            field=models.DateTimeField(null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layer',
            name='status_mosaic_error',
            field=models.CharField(max_length=255, null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layer',
            name='status_mosaic_start',
            field=models.DateTimeField(null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layer',
            name='status_thumbnail_end',
            field=models.DateTimeField(null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layer',
            name='status_thumbnail_error',
            field=models.CharField(max_length=255, null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layer',
            name='status_thumbnail_start',
            field=models.DateTimeField(null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layer',
            name='status_upload_end',
            field=models.DateTimeField(null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layer',
            name='status_upload_error',
            field=models.CharField(max_length=255, null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layer',
            name='status_upload_start',
            field=models.DateTimeField(null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layer',
            name='status_validate_end',
            field=models.DateTimeField(null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layer',
            name='status_validate_error',
            field=models.CharField(max_length=255, null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layer',
            name='status_validate_start',
            field=models.DateTimeField(null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layerimage',
            name='status_created',
            field=models.DateTimeField(default=datetime.datetime(2015, 10, 27, 21, 47, 54, 763011, tzinfo=utc), auto_now_add=True),
            preserve_default=False,
        ),
        migrations.AddField(
            model_name='layerimage',
            name='status_thumbnail_end',
            field=models.DateTimeField(null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layerimage',
            name='status_thumbnail_error',
            field=models.CharField(max_length=255, null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layerimage',
            name='status_thumbnail_start',
            field=models.DateTimeField(null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layerimage',
            name='status_upload_end',
            field=models.DateTimeField(null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layerimage',
            name='status_upload_error',
            field=models.CharField(max_length=255, null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layerimage',
            name='status_upload_start',
            field=models.DateTimeField(null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layerimage',
            name='status_validate_end',
            field=models.DateTimeField(null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layerimage',
            name='status_validate_error',
            field=models.CharField(max_length=255, null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layerimage',
            name='status_validate_start',
            field=models.DateTimeField(null=True, blank=True),
        ),
    ]
