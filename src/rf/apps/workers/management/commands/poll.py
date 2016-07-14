# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import logging

from django.core.management.base import BaseCommand

from apps.workers import process


log = logging.getLogger(__name__)


class Command(BaseCommand):
    def handle(self, *args, **kwargs):
        log.info('Begin polling')
        try:
            p = process.QueueProcessor()
            p.start_polling()
        except KeyboardInterrupt:
            pass
        except:
            log.exception('Polling aborted due to exception')
        log.info('Done polling')
