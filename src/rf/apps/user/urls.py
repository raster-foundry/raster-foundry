# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.conf.urls import patterns, url

from apps.user import views

urlpatterns = patterns(
    '',
    url('^logout$', views.logout_view, name='logout'),
    url('^login$', views.login_view, name='login'),
    url('^sign-up$', views.sign_up, name='sign_up'),
    url('^resend$', views.resend, name='resend'),
    url('^forgot$', views.forgot, name='forgot'),
    url('^activate/(?P<activation_key>[A-z0-9]+)/$', views.activate,
        name='activate'),
)
