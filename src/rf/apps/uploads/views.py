# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.contrib.auth.models import User

from rest_framework import serializers, viewsets
from rest_framework.response import Response
from rest_framework import decorators
from rest_framework.permissions import AllowAny
from rest_framework.renderers import StaticHTMLRenderer

from django.conf import settings
from django.shortcuts import render_to_response

import base64
import hmac, sha


@decorators.api_view(['GET'])
@decorators.permission_classes((AllowAny, ))
@decorators.renderer_classes((StaticHTMLRenderer, ))
def sign_upload_request(request, format=None):
    to_sign = str(request.REQUEST.get('to_sign'))
    key = settings.AWS_SECRET
    signature = base64.b64encode(hmac.new(str(key), to_sign, sha).digest())
    return Response(signature)
