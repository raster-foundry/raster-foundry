# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0031_status_start_end'),
    ]

    operations = [
        migrations.AddField(
            model_name='layer',
            name='dismissed',
            field=models.BooleanField(default=False),
        ),
    ]
