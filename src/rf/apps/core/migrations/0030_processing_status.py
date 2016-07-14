# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0029_add_statuses'),
    ]

    operations = [
        migrations.AlterField(
            model_name='layer',
            name='status',
            field=models.CharField(default='created', help_text='Processing workflow status of the layer', max_length=12, blank=True, choices=[('created', 'Created'), ('uploaded', 'Uploaded'), ('validated', 'Validated'), ('thumbnailed', 'Thumbnailed'), ('processing', 'Processing'), ('chunking', 'Chunking'), ('chunked', 'Chunked'), ('mosaicking', 'Mosaicking'), ('failed', 'Failed'), ('completed', 'Completed')]),
        ),
    ]
