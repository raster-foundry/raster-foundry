# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0023_update_thumbs_status'),
    ]

    operations = [
        migrations.AlterField(
            model_name='layer',
            name='error',
            field=models.CharField(help_text='Error that occured while processing the layer.', max_length=255, null=True, blank=True),
        ),
        migrations.AlterField(
            model_name='layerimage',
            name='error',
            field=models.CharField(help_text='Error that occured while processing the file.', max_length=255, null=True, blank=True),
        ),
    ]
