# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0033_layer_status_heartbeat'),
    ]

    operations = [
        migrations.RemoveField(
            model_name='layer',
            name='status_upload_end',
        ),
        migrations.RemoveField(
            model_name='layer',
            name='status_upload_error',
        ),
        migrations.RemoveField(
            model_name='layer',
            name='status_upload_start',
        ),
        migrations.RemoveField(
            model_name='layerimage',
            name='status_upload_end',
        ),
        migrations.RemoveField(
            model_name='layerimage',
            name='status_upload_start',
        ),
        migrations.AddField(
            model_name='layerimage',
            name='status_transfer_end',
            field=models.DateTimeField(null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layerimage',
            name='status_transfer_error',
            field=models.CharField(max_length=255, null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layerimage',
            name='status_transfer_start',
            field=models.DateTimeField(null=True, blank=True),
        ),
    ]
