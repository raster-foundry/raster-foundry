# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

# NEEDED WHEN WE ADD PROPER AUTHORIZATION
# from django.contrib.auth.models import User

from rest_framework.response import Response
from rest_framework import decorators
from rest_framework.permissions import AllowAny
from rest_framework.renderers import StaticHTMLRenderer

from django.conf import settings

import boto
import boto.auth
import boto.provider


# TODO - THIS SHOULD REQUIRE AUTHORIZATION.
# TODO - WE'LL WANT TO ENSURE THE USER CAN IN FACT ADD NEW CONTENT TO THE
# BUCKET BEFORE WE SIGN THE REQUEST.
@decorators.api_view(['GET'])
@decorators.permission_classes((AllowAny, ))
@decorators.renderer_classes((StaticHTMLRenderer, ))
def sign_upload_request(request, format=None):
    to_sign = str(request.REQUEST.get('to_sign'))
    provider = boto.provider.Provider('aws', profile_name=settings.AWS_PROFILE)
    signer = boto.auth.HmacAuthV1Handler(None, None, provider)
    signature = signer.sign_string(to_sign)

    return Response(signature)
