# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division


class ApiViewException(Exception):

    def to_json(self):
        data = {'message': self.message}
        if hasattr(self, 'errors'):
            data.update({'errors': self.errors})
        return data, self.status_code


class BadRequest(ApiViewException):
    status_code = 400
    message = 'Bad Request'

    def __init__(self, errors=None):
        # The format of `errors` should be:
        # { 'field_name': ['error message 1', 'error message 2', ...], ... }
        self.errors = errors


class Forbidden(ApiViewException):
    status_code = 403
    message = 'Forbidden'

    def __init__(self, errors=None):
        # The format of `errors` should be:
        # { 'field_name': ['error message 1', 'error message 2', ...], ... }
        self.errors = errors


class InvalidApiCredentials(ApiViewException):
    status_code = 401
    message = 'Invalid API credentials'


class MethodNotAllowed(ApiViewException):
    status_code = 405
    message = 'Method Not Allowed'


class Unauthorized(ApiViewException):
    status_code = 401
    message = 'Unauthorized'
