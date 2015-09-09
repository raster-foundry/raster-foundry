# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import json
from functools import wraps

from django.http import HttpResponse, QueryDict
from django.http.response import Http404

from apps.core.exceptions import (ApiViewException,
                                  Forbidden,
                                  InvalidApiCredentials,
                                  MethodNotAllowed,
                                  Unauthorized)
from apps.core.models import User


def api_view(fn):
    """
    Convert raised HTTP exceptions into a valid HttpResponse
    with the correct status code.
    """
    @wraps(fn)
    def wrapper(request, *args, **kwargs):
        headers = {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Headers': 'Content-Type',
            'Access-Control-Max-Age': '1000',
        }

        try:
            allowed_methods = getattr(fn, 'accepts', [])

            if request.method == 'OPTIONS':
                headers.update({
                    'Access-Control-Allow-Methods': ','.join(allowed_methods)
                })
                return create_response(status_code=200, headers=headers)
            elif request.method not in allowed_methods:
                raise MethodNotAllowed()

            if request.method == 'PUT':
                request.PUT = QueryDict(request.body)

            handle_api_authentication(request)

            data = fn(request, *args, **kwargs)

            # Don't do anything if a view returned a fully formed HttpResponse.
            if isinstance(data, HttpResponse):
                return data

            if data is None:
                output = ''
            else:
                output = serialize(data)

            status_code = 200
            return create_response(output, data, status_code, headers)
        except ApiViewException as ex:
            data, status_code = ex.to_json()
            output = serialize(data)
            return create_response(output, data, status_code, headers)
        except Http404:
            # Let Django handle 404 responses.
            raise
    return wrapper


def login_required(fn):
    """
    Restrict view to authenticated users only.
    """
    @wraps(fn)
    def wrapper(request, *args, **kwargs):
        if request.user.is_authenticated():
            return fn(request, *args, **kwargs)
        raise Unauthorized()
    return wrapper


def owner_required(fn):
    """
    Assert that a `username` URL field exists and that it belongs to
    the currently authenticated user.

    This should probably decorate any function that handles POST, PUT,
    or DELETE requests.

    Any function using this decorator must accept a keyword argument
    for `username`.
    """
    @wraps(fn)
    def wrapper(request, *args, **kwargs):
        username = kwargs.get('username', None)
        matches = username and request.user.username == username

        if matches or request.user.is_staff:
            return fn(request, *args, **kwargs)

        raise Unauthorized()
    return wrapper


def accepts(*verbs):
    """
    Associate a list of allowed HTTP methods to a view.
    """
    def wrapper(fn):
        fn.accepts = verbs
        return fn
    return wrapper


def serialize(data):
    """
    Convert view data to a JSON serialized HttpResponse.
    """
    return json.dumps(data)


def create_response(content='', data=None, status_code=200, headers={}):
    response = HttpResponse(status=status_code)

    for k, v in headers.iteritems():
        response[k] = v

    # To support blank responses for PUT, DELETE, and OPTIONS.
    if len(content) > 0:
        response.content = content

    # For unit tests.
    response.data = data

    return response


def handle_api_authentication(request):
    """
    *** This implementation is for testing purposes only! ***
    """
    if request.user.is_authenticated():
        return

    if 'api_key' not in request.REQUEST:
        return

    try:
        api_key = int(request.REQUEST.get('api_key'))
    except:
        raise Forbidden(errors={
            'api_key': ['Invalid API key format']
        })

    try:
        request.user = User.objects.get(id=api_key)
    except User.DoesNotExist:
        raise InvalidApiCredentials()
