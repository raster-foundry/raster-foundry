# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0032_add_dismissed'),
    ]

    operations = [
        migrations.AddField(
            model_name='layer',
            name='status_heartbeat',
            field=models.DateTimeField(null=True, blank=True),
        ),
    ]
