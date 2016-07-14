# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0018_merge'),
    ]

    operations = [
        migrations.AddField(
            model_name='layerimage',
            name='file_extension',
            field=models.CharField(default='', help_text='Extension of file', max_length=10),
        ),
    ]
