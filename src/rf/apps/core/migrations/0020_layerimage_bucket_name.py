# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0019_layerimage_file_extension'),
    ]

    operations = [
        migrations.AddField(
            model_name='layerimage',
            name='bucket_name',
            field=models.CharField(default='', help_text='Name of S3 bucket', max_length=255),
        ),
    ]
