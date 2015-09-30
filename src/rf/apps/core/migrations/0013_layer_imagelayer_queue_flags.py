# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0012_auto_20150923_1246'),
    ]

    operations = [
        migrations.AddField(
            model_name='layer',
            name='processed',
            field=models.BooleanField(default=False),
        ),
        migrations.AddField(
            model_name='layerimage',
            name='reprojected',
            field=models.BooleanField(default=False),
        ),
        migrations.AddField(
            model_name='layerimage',
            name='uploaded',
            field=models.BooleanField(default=False),
        ),
        migrations.AddField(
            model_name='layerimage',
            name='verified',
            field=models.BooleanField(default=False),
        ),
    ]
