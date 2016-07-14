# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0001_initial'),
    ]

    operations = [
        migrations.AddField(
            model_name='layermeta',
            name='bounds',
            field=models.CharField(help_text='JSON array', max_length=120, null=True),
        ),
        migrations.AddField(
            model_name='layermeta',
            name='center',
            field=models.CharField(help_text='JSON array', max_length=60, null=True),
        ),
        migrations.AddField(
            model_name='layermeta',
            name='max_zoom',
            field=models.IntegerField(default=11),
        ),
        migrations.AddField(
            model_name='layermeta',
            name='min_zoom',
            field=models.IntegerField(default=0),
        ),
    ]
