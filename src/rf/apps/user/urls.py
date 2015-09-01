# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.conf.urls import patterns, url

from apps.user.views import (login,
                             sign_up,
                             forgot,
                             resend,
                             logout,
                             activate)

urlpatterns = patterns(
    '',
    url(r'^logout$', logout, name='logout'),
    url(r'^login$', login, name='login'),
    url(r'^sign-up$', sign_up, name='sign_up'),
    url(r'^resend$', resend, name='resend'),
    url(r'^forgot$', forgot, name='forgot'),
    url(r'^activate/(?P<activation_key>[A-z0-9]+)/$',
        activate, name='activate'),
)
