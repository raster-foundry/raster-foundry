# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0004_auto_20150819_1246'),
    ]

    operations = [
        migrations.AlterField(
            model_name='layertag',
            name='layer',
            field=models.ForeignKey(related_name='layer_tags', to='core.Layer'),
        ),
    ]
