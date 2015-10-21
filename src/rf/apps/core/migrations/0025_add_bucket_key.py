# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0024_allow_null_errors'),
    ]

    operations = [
        migrations.AddField(
            model_name='layerimage',
            name='source_s3_bucket_key',
            field=models.CharField(default='', help_text='S3 <bucket>/<key> for source image (optional)', max_length=255),
        ),
        migrations.AlterField(
            model_name='layer',
            name='thumb_large_key',
            field=models.CharField(default='', help_text='S3 key for large thumbnail', max_length=255, blank=True),
        ),
        migrations.AlterField(
            model_name='layer',
            name='thumb_small_key',
            field=models.CharField(default='', help_text='S3 key for small thumbnail', max_length=255, blank=True),
        ),
        migrations.AlterField(
            model_name='layerimage',
            name='thumb_large_key',
            field=models.CharField(default='', help_text='S3 key for large thumbnail', max_length=255, blank=True),
        ),
        migrations.AlterField(
            model_name='layerimage',
            name='thumb_small_key',
            field=models.CharField(default='', help_text='S3 key for small thumbnail', max_length=255, blank=True),
        ),
    ]
