# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import models, migrations


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0003_auto_20150819_1200'),
    ]

    operations = [
        migrations.AlterUniqueTogether(
            name='layer',
            unique_together=set([('user', 'name')]),
        ),
    ]
