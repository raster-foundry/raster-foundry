# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0006_layer_is_public'),
    ]

    operations = [
        migrations.AlterField(
            model_name='userfavoritelayer',
            name='layer',
            field=models.ForeignKey(related_name='favorites', to='core.Layer'),
        ),
    ]
