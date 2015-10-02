# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0012_auto_20150923_1246'),
    ]

    operations = [
        migrations.AddField(
            model_name='layerimage',
            name='status',
            field=models.CharField(help_text='Image processing workflow status of the image', max_length=255, null=True, blank=True),
        ),
    ]
