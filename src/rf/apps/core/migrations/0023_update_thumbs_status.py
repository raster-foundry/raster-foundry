# -*- coding: utf-8 -*-
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('core', '0022_add_error_fields'),
    ]

    operations = [
        migrations.RemoveField(
            model_name='layer',
            name='thumb_large',
        ),
        migrations.RemoveField(
            model_name='layer',
            name='thumb_small',
        ),
        migrations.RemoveField(
            model_name='layerimage',
            name='thumb_large',
        ),
        migrations.RemoveField(
            model_name='layerimage',
            name='thumb_small',
        ),
        migrations.AddField(
            model_name='layer',
            name='thumb_large_key',
            field=models.CharField(default='', help_text='400x150 pixels', max_length=255, blank=True),
        ),
        migrations.AddField(
            model_name='layer',
            name='thumb_small_key',
            field=models.CharField(default='', help_text='80x80 pixels', max_length=255, blank=True),
        ),
        migrations.AddField(
            model_name='layerimage',
            name='thumb_large_key',
            field=models.CharField(default='', help_text='300x300 pixels', max_length=255, blank=True),
        ),
        migrations.AddField(
            model_name='layerimage',
            name='thumb_small_key',
            field=models.CharField(default='', help_text='80x80 pixels', max_length=255, blank=True),
        ),
        migrations.AlterField(
            model_name='layer',
            name='status',
            field=models.CharField(default='created', help_text='Processing workflow status of the layer', max_length=12, blank=True, choices=[('created', 'Created'), ('uploaded', 'Uploaded'), ('validated', 'Validated'), ('thumbnailed', 'Thumbnailed'), ('processing', 'Processing'), ('failed', 'Failed'), ('completed', 'Completed')]),
        ),
        migrations.AlterField(
            model_name='layerimage',
            name='status',
            field=models.CharField(default='created', help_text='Image processing workflow status of the image', max_length=12, blank=True, choices=[('created', 'Created'), ('uploaded', 'Uploaded'), ('valid', 'Valid'), ('invalid', 'Invalid'), ('thumbnailed', 'Thumbnailed')]),
        ),
    ]
