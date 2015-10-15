# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0021_remove_layerimage_source_uri'),
    ]

    operations = [
        migrations.AddField(
            model_name='layer',
            name='error',
            field=models.CharField(help_text='Error that occured while processing the layer.', max_length=255, blank=True),
        ),
        migrations.AddField(
            model_name='layerimage',
            name='error',
            field=models.CharField(help_text='Error that occured while processing the file.', max_length=255, blank=True),
        ),
        migrations.AlterField(
            model_name='layerimage',
            name='status',
            field=models.CharField(default='created', help_text='Image processing workflow status of the image', max_length=12, blank=True, choices=[('created', 'Created'), ('uploaded', 'Uploaded'), ('valid', 'Valid'), ('invalid', 'Invalid')]),
        ),
    ]
