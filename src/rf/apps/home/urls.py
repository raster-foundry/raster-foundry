# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.conf.urls import patterns, url, include
from rest_framework.routers import DefaultRouter

from apps.home.views import home_page
from apps.home.views import (LayerListView,
                             UserLayerViewSet,
                             FavoriteListView,
                             FavoriteCreateDestroyView)


username_regex = r'[\w.@+-]+'
slug_regex = r'[-_\w]+'

# Use router for UserLayerViewSet to generate urls automatically. This
# can only be done for ViewSets.
router = DefaultRouter()
router.register(r'user/(?P<username>' + username_regex + r')/layers',
                UserLayerViewSet, base_name='user_layers')


urlpatterns = patterns(
    '',
    url(r'^$', home_page, name='home_page'),
    url(r'^', include(router.urls)),
    url(r'user/(?P<username>' + username_regex + r')/favorites/',
        FavoriteListView.as_view()),
    url(r'user/(?P<username>' + username_regex + r')/layers/(?P<slug>' +
        slug_regex + r')/favorite/',
        FavoriteCreateDestroyView.as_view()),
    url(r'layers/', LayerListView.as_view()),
)
