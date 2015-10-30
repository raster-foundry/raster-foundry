# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import json
from boto import provider

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
    creds = provider.Provider('aws')
    client_settings = {
        'signerUrl': reverse('sign_request'),
        'awsKey': creds.access_key,
        'awsToken': creds.security_token,
        'awsBucket': settings.AWS_BUCKET_NAME,
    }
    return client_settings
