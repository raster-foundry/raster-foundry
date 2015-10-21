# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import boto3

from django.conf import settings


def s3_copy(source_s3_bucket_key, destination_key):
    """
    Copy a source file to destination_key in AWS_BUCKET_NAME.
    source_s3_bucket_key -- string of the form <bucket>/<key>
    """

    session = boto3.session.Session()
    s3_client = session.client('s3')
    try:
        s3_client.copy_object(CopySource=source_s3_bucket_key,
                              Bucket=settings.AWS_BUCKET_NAME,
                              Key=destination_key)
        return True
    except:
        return False
