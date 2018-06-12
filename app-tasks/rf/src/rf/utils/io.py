import os
import re
import tempfile
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


def base_metadata_for_landsat_id(landsat_id):
    pattern = re.compile(r'L(?P<sensor_id>.)(?P<landsat_number>\d)(?P<path>\d{3})(?P<row>\d{3}).*')
    match = pattern.match(landsat_id)
    if not match:
        raise Exception('Could not parse Landsat ID. Make sure you entered it correctly.')
    return match.groupdict()


def gcs_path_for_landsat_id(landsat_id):
    """Create a Google Cloud Storage path from a Landsat ID
    """
    metadata = base_metadata_for_landsat_id(landsat_id)
    tmpl = (
        'https://storage.googleapis.com/gcp-public-data-landsat/'
        'L{sensor_id}0{landsat_number}/PRE/{path}/{row}/{landsat_id}'
    )
    return tmpl.format(**dict(landsat_id=landsat_id, **metadata))


def make_fname_for_band(band, landsat_id):
    return '{}_B{}.TIF'.format(landsat_id, band)


def make_fname_for_mtl(landsat_id):
    return '{}_MTL.txt'.format(landsat_id)


def make_path_for_band(prefix, band, landsat_id):
    return '/'.join([prefix, make_fname_for_band(band, landsat_id)])


def make_path_for_mtl(prefix, landsat_id):
    return '/'.join([prefix, make_fname_for_mtl(landsat_id)])


def get_jwt():
    """Fetch an access token from the Auth0 API"""
    r = requests.post(
        'https://{}/oauth/token'.format(
            os.getenv('AUTH0_DOMAIN')
        ),
        data={
            'grant_type': 'refresh_token',
            'client_id': os.getenv('AUTH0_CLIENT_ID'),
            'refresh_token': os.getenv('AUTH0_SYSTEM_REFRESH_TOKEN')
        }
    )
    r.raise_for_status()
    json = r.json()
    return json['id_token']


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
