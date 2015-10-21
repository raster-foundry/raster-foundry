# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0025_add_bucket_key'),
    ]

    operations = [
        migrations.AlterField(
            model_name='layerimage',
            name='source_s3_bucket_key',
            field=models.CharField(help_text='S3 <bucket>/<key> for source image (optional)', max_length=255, null=True),
        ),
    ]
