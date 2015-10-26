# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.http import JsonResponse
from django.db import connections

from apps.core.decorators import accepts


@accepts('GET')
def health_check(request):
    response = {}

    for check in [_check_app_servers, _check_database]:
        response.update(check())

    if all(map(lambda x: x[0]['default']['ok'], response.values())):
        return JsonResponse(response, status=200)
    else:
        return JsonResponse(response, status=503)


def _check_database(database='default'):
    try:
        connections[database].introspection.table_names()

        response = {database: {'ok': True}}
    except Exception as e:
        response = {
            database: {
                'ok': False,
                'msg': str(e)
            },
        }

    return {'databases': [response]}


def _check_app_servers():
    return {'apps': [{'default': {'ok': True}}]}
