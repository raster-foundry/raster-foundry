from datetime import datetime, timedelta
import os
import tempfile
from urlparse import urlparse

import boto3
import requests
import jwt


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


def download_s3_obj_by_key(bucket, key):
    """Helper function to download an object in s3 to /tmp

    Args:
        bucket (str): an s3 bucket
        key (str): an s3 key

    Returns:
        str: the name of the tempfile holding the s3 object
    """

    client = boto3.client('s3')
    obj = client.get_object(Bucket=bucket, Key=key)
    tf = tempfile.mktemp()
    with open(tf, 'wb') as outf:
        outf.write(obj['Body'].read())
    return tf

def s3_obj_exists(url):
    """Helper function to determine whether an s3 object exists

    Args:
        url (str): https URL for a public object stored in s3
    """
    resp = requests.head(url)
    return resp.status_code != 404


def get_jwt():
    """Construct JSON web token for auth purposes"""

    jwt_secret = os.getenv('AUTH0_CLIENT_SECRET')
    claims = {
        'sub': 'rf|airflow-user',
        'iat': datetime.utcnow(),
        'exp': datetime.utcnow() + timedelta(hours=3)
    }
    encoded_jwt = jwt.encode(claims, jwt_secret, algorithm='HS256')
    return encoded_jwt


def get_session():
    """Helper method to create a requests Session"""

    encoded_jwt = get_jwt()
    session = requests.Session()

    session.headers.update({'Authorization': 'Bearer {}'.format(encoded_jwt)})
    return session


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
