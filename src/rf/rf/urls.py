# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.conf.urls import patterns, include, url
from django.contrib import admin
from rest_framework import routers

import apps.home.urls
import apps.core.urls
import apps.user.urls

admin.autodiscover()

urlpatterns = patterns(
    '',
    url(r'^', include(apps.home.urls)),
    url(r'^', include(apps.core.urls)),
    url(r'^user/', include(apps.user.urls)),
    url(r'^admin/', include(admin.site.urls)),
)
