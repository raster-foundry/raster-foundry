# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.core.management.base import BaseCommand

from apps.workers import process


class Command(BaseCommand):
    def handle(self, *args, **options):
        self.stdout.write('Polling started in worker queue.\n')
        p = process.QueueProcessor()
        p.start_polling()
