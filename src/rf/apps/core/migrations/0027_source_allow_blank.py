# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0026_bucket_key_null'),
    ]

    operations = [
        migrations.AlterField(
            model_name='layerimage',
            name='source_s3_bucket_key',
            field=models.CharField(help_text='S3 <bucket>/<key> for source image (optional)', max_length=255, null=True, blank=True),
        ),
    ]
