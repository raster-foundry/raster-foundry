# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0015_auto_20151005_1818'),
    ]

    operations = [
        migrations.AlterField(
            model_name='layer',
            name='status',
            field=models.CharField(default='started', help_text='Processing workflow status of the layer', max_length=12, blank=True, choices=[('started', 'Started'), ('uploaded', 'Uploaded'), ('validated', 'Validated'), ('reprojected', 'Reprojected'), ('processing', 'Processing'), ('failed', 'Failed'), ('completed', 'Completed')]),
        ),
    ]
