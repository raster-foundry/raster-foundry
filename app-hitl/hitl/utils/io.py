from contextlib import contextmanager
import logging
import os
import re
import shutil
import subprocess
import tempfile
from urllib.parse import urlparse, unquote, quote

import boto3
import requests

logger = logging.getLogger(__name__)
logging.basicConfig(format='%(levelname)s:%(message)s', level=logging.INFO)


@contextmanager
def get_tempdir(debug=False):
    """Returns a temporary directory that is cleaned up after usage

    Returns:
        str
    """
    temp_dir = tempfile.mkdtemp()
    try:
        yield temp_dir
    finally:
        if not debug:
            shutil.rmtree(temp_dir)


def s3_bucket_and_key_from_url(s3_url):
    """
    Raises:
      ValueError
    """
    parts = urlparse(s3_url)
    # parts.path[1:] drops the leading slash that urlparse includes
    return (parts.netloc, parts.path[1:])


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
    s3 = boto3.resource("s3")
    parsed_url = urlparse(url)
    if parsed_url.scheme == "s3":
        return s3.Object(parsed_url.netloc, parsed_url.path)
    elif parsed_url.scheme == "https" and parsed_url.netloc == "s3.amazonaws.com":
        parts = parsed_url.path[1:].split("/")
        bucket = parts[0]
        key = "/".join(parts[1:])
        return s3.Object(bucket, key)


def download_s3_obj_by_key(bucket, key):
    """Helper function to download an object in s3 to /tmp

    Args:
        bucket (str): an s3 bucket
        key (str): an s3 key

    Returns:
        str: the name of the tempfile holding the s3 object
    """

    client = boto3.client("s3")
    obj = client.get_object(Bucket=bucket, Key=key)
    tf = tempfile.mktemp()
    with open(tf, "wb") as outf:
        outf.write(obj["Body"].read())
    return tf


def s3_obj_exists(url):
    """Helper function to determine whether an s3 object exists

    Args:
        url (str): https URL for a public object stored in s3
    """
    resp = requests.head(url)
    return resp.status_code != 404


def get_jwt():
    """Fetch an access token from the Auth0 API"""
    r = requests.post(
        "https://{}/oauth/token".format(os.getenv("AUTH0_DOMAIN")),
        data={
            "grant_type": "refresh_token",
            "client_id": os.getenv("AUTH0_CLIENT_ID"),
            "refresh_token": os.getenv("AUTH0_SYSTEM_REFRESH_TOKEN"),
        },
    )
    r.raise_for_status()
    json = r.json()
    return json["id_token"]


def get_session():
    """Helper method to create a requests Session"""

    encoded_jwt = get_jwt()
    session = requests.Session()

    session.headers.update({"Authorization": "Bearer {}".format(encoded_jwt)})
    session.headers.update({"User-Agent": "RF/App-HITL Client"})
    return session
