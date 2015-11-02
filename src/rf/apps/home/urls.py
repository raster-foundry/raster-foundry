# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.conf.urls import patterns, url, include

from apps.home import views


slug_regex = r'[-_\w]+'
username_regex = r'[\w.@+-]+'


user_patterns = [
    url('^/imports.json$', views.user_layers, name='user_layers'),
    url('^/layer/create/?$', views.create_layer, name='create_layer'),
    url('^/layer/(?P<layer_id>\d+).json$', views.layer_detail,
        name='layer_detail'),
    url('^/layer/meta/(?P<layer_id>\d+).json$', views.layer_meta,
        name='layer_meta'),
]

urlpatterns = patterns(
    '',
    url('^user/(?P<username>' + username_regex + ')', include(user_patterns)),
    url('^imports.json$', views.my_layers, name='imports'),
    url('^favorites.json$', views.my_favorites, name='favorites'),
    url('^favorite/(?P<layer_id>\d+)$', views.create_or_destroy_favorite,
        name='create_or_destroy_favorite'),
    url('^catalog.json$', views.all_layers, name='catalog'),
    url('^layer/dismiss', views.layer_dismiss, name='layer_dismiss'),
    url('^layer/retry', views.layer_retry, name='layer_retry'),

    # These all route to the home page.
    url('^imports/?$', views.home_page),
    url('^catalog/?$', views.home_page),
    url('^favorites/?$', views.home_page),
    url('^login/?$', views.home_page),
    url('^sign-up/?$', views.home_page),
    url('^send-activation/?$', views.home_page),
    url('^forgot/?$', views.home_page),
    url('^logout/?$', views.home_page),
    url('^activate/?$', views.home_page),
    url('^reset-password/\S+/\S+/?', views.home_page),
    url('^keys/?$', views.home_page),
    url('^billing/?$', views.home_page),
    url('^account/?$', views.home_page),
    url('^$', views.home_page, name='home_page'),

    url('', views.not_found, name='not_found'),
)
