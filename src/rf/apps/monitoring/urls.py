# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.conf.urls import patterns, url
from apps.monitoring.views import health_check


urlpatterns = patterns(
    '',
    url(r'^$', health_check, name='health_check'),
)
