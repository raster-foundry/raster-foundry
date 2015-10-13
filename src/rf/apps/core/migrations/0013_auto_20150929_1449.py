# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0012_auto_20150923_1246'),
    ]

    operations = [
        migrations.AddField(
            model_name='layer',
            name='thumb_large',
            field=models.URLField(help_text='400x150 pixels', null=True, blank=True),
        ),
        migrations.AddField(
            model_name='layer',
            name='thumb_small',
            field=models.URLField(help_text='80x80 pixels', null=True, blank=True),
        ),
    ]
