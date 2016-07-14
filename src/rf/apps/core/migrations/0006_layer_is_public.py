# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0005_auto_20150820_1031'),
    ]

    operations = [
        migrations.AddField(
            model_name='layer',
            name='is_public',
            field=models.BooleanField(default=False),
        ),
    ]
