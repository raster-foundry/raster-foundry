# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations
import uuid


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0007_auto_20150820_1801'),
    ]

    operations = [
        migrations.AddField(
            model_name='layerimage',
            name='file_name',
            field=models.CharField(default='', help_text='Filename of original file', max_length=255),
        ),
        migrations.AddField(
            model_name='layerimage',
            name='s3_uuid',
            field=models.UUIDField(default=uuid.uuid4, editable=False),
        ),
    ]
