# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.conf.urls import patterns, include, url
from django.contrib import admin

import registration.backends.default.urls

import apps.home.urls
import apps.user.urls
import apps.uploads.urls


admin.autodiscover()

urlpatterns = patterns(
    '',
    url(r'^admin/', include(admin.site.urls)),
    url(r'^uploads/', include(apps.uploads.urls)),
    url(r'^user/', include(apps.user.urls)),
    url(r'^accounts/', include(registration.backends.default.urls)),
    url(r'^', include(apps.home.urls)),
)
