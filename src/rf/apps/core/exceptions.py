# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division


class BadRequest(Exception):

    def __init__(self, errors=None):
        # The format of `errors` should be:
        # { 'field_name': ['error message 1', 'error message 2', ...], ... }
        self.errors = errors


class Forbidden(Exception):

    def __init__(self, errors=None):
        # The format of `errors` should be:
        # { 'field_name': ['error message 1', 'error message 2', ...], ... }
        self.errors = errors


class InvalidApiCredentials(Exception):
    pass


class MethodNotAllowed(Exception):
    pass


class Unauthorized(Exception):
    pass
