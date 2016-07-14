# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations
import datetime


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0014_layer_status'),
    ]

    operations = [
        migrations.AddField(
            model_name='layer',
            name='status_updated_at',
            field=models.DateTimeField(default=datetime.datetime.now),
        ),
        migrations.AlterField(
            model_name='layer',
            name='status',
            field=models.CharField(default='started', help_text='Processing workflow status of the layer', max_length=12, blank=True, choices=[('started', 'Started'), ('uploaded', 'Uploaded'), ('validated', 'Validated'), ('reprojected', 'Reprojected'), ('processing', 'Processing'), ('failed', 'Failed'), ('complete', 'Complete')]),
        ),
        migrations.AlterField(
            model_name='layerimage',
            name='status',
            field=models.CharField(default='started', help_text='Image processing workflow status of the image', max_length=12, blank=True, choices=[('started', 'Started'), ('uploaded', 'Uploaded'), ('validated', 'Validated')]),
        ),
    ]
