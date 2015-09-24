# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0010_auto_20150915_1421'),
    ]

    operations = [
        migrations.AlterField(
            model_name='layer',
            name='transparency',
            field=models.CharField(help_text='Hexadecimal (Ex. #00FF00)', max_length=18, blank=True),
        ),
    ]
