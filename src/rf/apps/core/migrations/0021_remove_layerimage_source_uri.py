# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0020_layerimage_bucket_name'),
    ]

    operations = [
        migrations.RemoveField(
            model_name='layerimage',
            name='source_uri',
        ),
    ]
