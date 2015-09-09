# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import json
import boto

from django.conf import settings
from django.core.urlresolvers import reverse


def page_context(request):
    """
    Context available on every page.
    """
    return {
        'user_data': get_user_data(request),
        'client_settings': get_client_settings(),
    }


def get_user_data(request):
    user_data = json.dumps({
        'id': request.user.id,
        'username': request.user.username,
    })
    return user_data


def get_client_settings():
    conn = boto.connect_s3(profile_name=settings.AWS_PROFILE)
    aws_key = conn.aws_access_key_id

    client_settings = json.dumps({
        'signerUrl': reverse('sign_request'),
        'awsKey': aws_key,
        'awsBucket': settings.AWS_BUCKET_NAME,
    })
    return client_settings
