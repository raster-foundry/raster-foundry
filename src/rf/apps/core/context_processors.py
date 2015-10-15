# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import json
import boto

from django.conf import settings
from django.core.urlresolvers import reverse

from apps.user.views import get_user_data


def page_context(request):
    """
    Context available on every page.
    """
    return {
        'user_data': json.dumps(get_user_data(request)),
        'client_settings': json.dumps(get_client_settings()),
    }


def get_client_settings():
    conn = boto.connect_s3()
    aws_key = conn.aws_access_key_id
    client_settings = {
        'signerUrl': reverse('sign_request'),
        'awsKey': aws_key,
        'awsBucket': settings.AWS_BUCKET_NAME,
    }
    return client_settings
