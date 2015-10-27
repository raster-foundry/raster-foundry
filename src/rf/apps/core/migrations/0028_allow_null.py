# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0027_source_allow_blank'),
    ]

    operations = [
        migrations.AlterField(
            model_name='layer',
            name='area',
            field=models.FloatField(default=0, null=True, blank=True),
        ),
        migrations.AlterField(
            model_name='layer',
            name='capture_end',
            field=models.DateField(null=True, blank=True),
        ),
        migrations.AlterField(
            model_name='layer',
            name='capture_start',
            field=models.DateField(null=True, blank=True),
        ),
        migrations.AlterField(
            model_name='layer',
            name='organization',
            field=models.CharField(max_length=255, null=True, blank=True),
        ),
    ]
