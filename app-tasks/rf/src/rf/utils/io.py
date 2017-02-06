import os
from urlparse import urlparse

import boto3
import requests


s3 = boto3.resource('s3', region_name='eu-central-1')


def delete_file(fname):
    """Helper function to delete a file without raising an error if it exists"""

    try:
        os.remove(fname)
    except OSError:
        pass


def create_s3_obj(url):
    """Helper function to create an S3 object from a URL

    This function's purpose is to create a boto s3 object from a URL

    Args:
        url (str): either an s3 or https URL for an object stored in S3
    """
    s3 = boto3.resource('s3')
    parsed_url = urlparse(url)
    if parsed_url.scheme == 's3':
        return s3.Object(parsed_url.netloc, parsed_url.path)
    elif parsed_url.scheme == 'https' and parsed_url.netloc == 's3.amazonaws.com':
        parts = parsed_url.path[1:].split('/')
        bucket = parts[0]
        key = '/'.join(parts[1:])
        return s3.Object(bucket, key)


def s3_obj_exists(url):
    """Helper function to determine whether an s3 object exists

    Args:
        url (str): https URL for a public object stored in s3
    """
    resp = requests.head(url)
    return resp.status_code != 404


class JobStatus(object):
    QUEUED = 'QUEUED'
    PROCESSING = 'PROCESSING'
    FAILURE = 'FAILURE'
    SUCCESS = 'SUCCESS'
    UPLOADING = 'UPLOADING'
    PARTIALFAILURE = 'PARTIALFAILURE'

class IngestStatus(object):
    NOTINGESTED = 'NOTINGESTED'
    TOBEINGESTED = 'TOBEINGESTED'
    INGESTING = 'INGESTING'
    INGESTED = 'INGESTED'
    FAILED = 'FAILED'

class Visibility(object):
    PUBLIC = 'PUBLIC'
    ORGANIZATION = 'ORGANIZATION'
    PRIVATE = 'PRIVATE'
