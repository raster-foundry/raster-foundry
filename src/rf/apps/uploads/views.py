# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.conf import settings
from django.http import HttpResponse

import boto
import boto.auth
import boto.provider

from apps.core.exceptions import BadRequest
from apps.core.decorators import accepts, api_view, login_required


@api_view
@accepts('GET')
@login_required
def sign_request(request):
    # TODO - WE'LL WANT TO ENSURE THE USER CAN IN FACT ADD NEW CONTENT TO THE
    # BUCKET BEFORE WE SIGN THE REQUEST.
    user_id = request.user.id
    bucket_name = settings.AWS_BUCKET_NAME + '/'
    profile = settings.AWS_PROFILE
    to_sign = str(request.REQUEST.get('to_sign'))

    # Extract file name from the string to sign and then extract the user
    # id from that.
    start = len(bucket_name) + to_sign.find(bucket_name)
    end = to_sign.rfind('?uploads', start)
    file_name = to_sign[start:end]
    sent_user_id = int(file_name[0:file_name.find('-')])

    # Safety check. The front end is not safe. Ensure that the user id
    # that came from front end JS matches the request user id before
    # signing.
    if sent_user_id != user_id:
        return BadRequest(errors={
            'all': ['User mismatch']
        })

    provider = boto.provider.Provider('aws', profile_name=profile)
    signer = boto.auth.HmacAuthV1Handler(None, None, provider)
    signature = signer.sign_string(to_sign)

    return HttpResponse(signature)
