# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.conf.urls import patterns, url

from apps.uploads.views import SignUploadRequestView


urlpatterns = patterns(
    '',
    url(r'^sign-request$', SignUploadRequestView.as_view(), name='sign_request'),
)
