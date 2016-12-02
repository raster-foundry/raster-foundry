import base64
from datetime import datetime, timedelta
import requests
import jwt
import json
import os


def get_session():
    """Helper method to create a requests Session"""

    jwt_secret = base64.urlsafe_b64decode(os.getenv('AUTH0_CLIENT_SECRET'))
    claims = {
        'sub': 'rf|airflow-user',
        'iat': datetime.utcnow(),
        'exp': datetime.utcnow() + timedelta(hours=3)
    }
    encoded_jwt = jwt.encode(claims, jwt_secret, algorithm='HS256')
    session = requests.Session()

    session.headers.update({'Authorization': 'Bearer {}'.format(encoded_jwt)})
    return session


class BaseModel(object):
    """Base class for all raster foundry models

    Abstracts out the following:
     - interaction with the API
     - creation of raster foundry objects given an ID from the API
     - creating a new object via the API
     - loading objects from JSON
    """
    HOST = os.getenv('RF_HOST')
    URL_PATH = None

    @classmethod
    def from_id(cls, id):
        url = '{HOST}{URL_PATH}{id}'.format(HOST=cls.HOST, URL_PATH=cls.URL_PATH, id=id)
        session = get_session()
        response = session.get(url)
        response.raise_for_status()

        return cls.from_json(response.json())

    @classmethod
    def from_dict(cls, d):
        raise NotImplementedError('from_dict is not implemented for this derived class')

    def to_dict(self):
        raise NotImplementedError('to_dict is not implemented for this derived class')

    def to_json(self):
        return json.dumps(self.to_dict())

    @classmethod
    def from_json(cls, json_string):
        return cls.from_dict(json_string)

    def create(self):
        url = '{HOST}{URL_PATH}'.format(HOST=self.HOST, URL_PATH=self.URL_PATH)
        session = get_session()
        response = session.post(url, json=self.to_dict())
        response.raise_for_status()
        return self.from_dict(response.json())
