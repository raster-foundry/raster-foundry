# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0013_imagelayer_status'),
    ]

    operations = [
        migrations.AddField(
            model_name='layer',
            name='status',
            field=models.CharField(help_text='Processing workflow status of the layer', max_length=255, null=True, blank=True),
        ),
    ]
