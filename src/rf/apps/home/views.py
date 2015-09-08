# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import json
import boto

from django.contrib.auth.models import User
from django.conf import settings
from django.core.urlresolvers import reverse
from django.db.models import Q
from django.shortcuts import get_object_or_404, Http404, render_to_response
from django.utils import timezone
from django.views.decorators.csrf import csrf_exempt

from apps.core.exceptions import Forbidden
from apps.core.decorators import (accepts, api_view, login_required,
                                  owner_required)
from apps.core.models import Layer, UserFavoriteLayer
from apps.home.forms import LayerForm
from apps.home.filters import LayerFilter


def home_page(request):
    return render_to_response('home/home.html',
                              {'client_settings': get_client_settings()})


def get_client_settings():
    conn = boto.connect_s3(profile_name=settings.AWS_PROFILE)
    aws_key = conn.aws_access_key_id

    client_settings = json.dumps({
        'signerUrl': reverse('sign_request'),
        'awsKey': aws_key,
        'awsBucket': settings.AWS_BUCKET_NAME,
    })
    return client_settings


@api_view
@accepts('GET')
def not_found(request):
    raise Http404()


@csrf_exempt
@api_view
@accepts('GET', 'PUT', 'DELETE')
def layer_detail(request, username, layer_id):
    layer = _get_layer_or_404(request, username, layer_id)
    if request.method == 'GET':
        return layer.to_json()
    elif request.method == 'PUT':
        return _save_layer(request, layer)
    elif request.method == 'DELETE':
        return _delete_layer(request, layer)


@api_view
@accepts('GET')
def layer_meta(request, username, layer_id):
    layer = _get_layer_or_404(request, username, layer_id)
    try:
        meta = layer.layer_metas.order_by('-created_at')[0]
        return meta.to_json()
    except IndexError:
        raise Http404()


def _get_layer_or_404(request, username, layer_id):
    try:
        crit = Q(id=layer_id, user__username=username)
        return _get_layer_models(request, crit)[0]
    except IndexError:
        raise Http404()


@csrf_exempt
@api_view
@accepts('POST')
def create_layer(request, username):
    layer = Layer()
    layer.user = request.user
    return _save_layer(request, layer)


@owner_required
def _save_layer(request, instance=None):
    """
    Create or update a layer model with data from POST or PUT form fields.
    """
    if request.method == 'POST':
        data = request.POST.copy()
    else:
        data = request.PUT.copy()

    # TODO: Check if user has already created a layer with this name.

    form = LayerForm(data, instance=instance)

    if not form.is_valid():
        raise Forbidden(errors=form.errors)

    try:
        layer = form.save()
    except Exception as ex:
        # TODO: Log exception
        raise Forbidden(errors={
            'all': ex.message
        })

    return layer.to_json()


@owner_required
def _delete_layer(request, layer):
    layer.deleted_at = timezone.now()
    layer.save()


@api_view
@accepts('GET')
def user_layers(request, username):
    get_object_or_404(User, username=username)
    return _get_layers(request, Q(user__username=username))


@api_view
@login_required
@accepts('GET')
def my_layers(request):
    return _get_layers(request, Q(user=request.user))


@api_view
@login_required
@accepts('GET')
def my_favorites(request):
    ids = UserFavoriteLayer.objects.filter(user__id=request.user.id) \
                                   .order_by('-created_at') \
                                   .select_related('layer') \
                                   .values_list('layer_id', flat=True)
    return _get_layers(request, Q(id__in=ids))


@csrf_exempt
@api_view
@login_required
@accepts('POST', 'DELETE')
def create_or_destroy_favorite(request, layer_id):
    """
    Create or destroy "favorited" layer for currently authenticated user.
    """
    kwargs = {
        'user_id': request.user.id,
        'layer_id': layer_id,
    }
    if request.method == 'POST':
        model, created = UserFavoriteLayer.objects.get_or_create(**kwargs)
        model.save()
    elif request.method == 'DELETE':
        model = get_object_or_404(UserFavoriteLayer, **kwargs)
        model.delete()


@api_view
@accepts('GET')
def all_layers(request):
    return _get_layers(request)


def _get_layer_models(request, crit=None):
    """
    Return list of filtered layer models.
    """
    qs = Layer.objects.select_related('user') \
                      .prefetch_related('layer_images', 'layer_tags')

    qs = qs.filter(deleted_at__isnull=True)

    if not request.user.is_staff:
        is_visible = Q(user__id=request.user.id) | Q(is_public=True)
        qs = qs.filter(is_visible)

    if crit:
        qs = qs.filter(crit)

    layers = LayerFilter(request.GET, queryset=qs)
    return layers


def _get_layers(request, crit=None):
    """
    Return list of JSON serializable layer models.
    """
    models = _get_layer_models(request, crit)
    return [m.to_json() for m in models]
