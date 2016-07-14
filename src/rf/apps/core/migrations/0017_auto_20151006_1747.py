# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0016_auto_20151006_1732'),
    ]

    operations = [
        migrations.AlterField(
            model_name='layer',
            name='status',
            field=models.CharField(default='created', help_text='Processing workflow status of the layer', max_length=12, blank=True, choices=[('created', 'Created'), ('uploaded', 'Uploaded'), ('validated', 'Validated'), ('reprojected', 'Reprojected'), ('processing', 'Processing'), ('failed', 'Failed'), ('completed', 'Completed')]),
        ),
        migrations.AlterField(
            model_name='layerimage',
            name='status',
            field=models.CharField(default='created', help_text='Image processing workflow status of the image', max_length=12, blank=True, choices=[('created', 'Created'), ('uploaded', 'Uploaded'), ('validated', 'Validated')]),
        ),
    ]
