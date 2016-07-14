# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0002_auto_20150817_1235'),
    ]

    operations = [
        migrations.AddField(
            model_name='layer',
            name='slug',
            field=models.SlugField(max_length=255, blank=True),
        ),
        migrations.AlterField(
            model_name='layerimage',
            name='layer',
            field=models.ForeignKey(related_name='layer_images', to='core.Layer'),
        ),
    ]
