# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.contrib.auth.models import User
from django.db import transaction
from django.db.models import Q
from django.shortcuts import get_object_or_404, Http404, render_to_response
from django.template import RequestContext
from django.utils import timezone
from django.views.decorators.csrf import ensure_csrf_cookie

from apps.core.exceptions import Forbidden
from apps.core.decorators import (accepts, api_view, login_required,
                                  owner_required)
from apps.core.models import Layer, LayerImage, LayerTag, UserFavoriteLayer
from apps.home.forms import LayerForm
from apps.home.filters import LayerFilter


@ensure_csrf_cookie
def home_page(request):
    context = RequestContext(request)
    return render_to_response('home/home.html', context)


@api_view
@accepts('GET')
def not_found(request):
    raise Http404()


@api_view
@accepts('GET', 'PUT', 'DELETE')
def layer_detail(request, username, layer_id):
    layer = _get_layer_or_404(request, id=layer_id, user__username=username)
    if request.method == 'GET':
        return layer.to_json()
    elif request.method == 'PUT':
        return _save_layer(request, layer, username=username)
    elif request.method == 'DELETE':
        return _delete_layer(request, layer, username=username)


@api_view
@accepts('GET')
def layer_meta(request, username, layer_id):
    layer = _get_layer_or_404(request, id=layer_id, user__username=username)
    try:
        meta = layer.layer_metas.order_by('-created_at')[0]
        return meta.to_json()
    except IndexError:
        raise Http404()


def _get_layer_or_404(request, **kwargs):
    try:
        crit = Q(**kwargs)
        return _get_layer_models(request, crit)[0]
    except IndexError:
        raise Http404()


@api_view
@login_required
@accepts('POST')
def create_layer(request, username):
    layer = Layer()
    layer.user = request.user
    return _save_layer(request, layer, username=username)


@transaction.atomic
@owner_required
def _save_layer(request, layer, username=None):
    """
    Create or update a layer model with data from POST or PUT form fields.
    """
    if request.method == 'POST':
        data = request.POST.copy()
    else:
        data = request.PUT.copy()

    data['tags'] = data.getlist('tags')
    data['images'] = data.getlist('images')

    form = LayerForm(data, instance=layer)

    if not form.is_valid():
        raise Forbidden(errors=form.errors)

    if Layer.objects.filter(user__username=request.user.username,
                            name=form.cleaned_data['name']).count():
        raise Forbidden(errors={
            'name': ['Layer with name already exists for user.']
        })

    try:
        layer = form.save()
    except Exception as ex:
        # TODO: Log exception
        raise Forbidden(errors={
            'all': ex.message
        })

    # Update tags.
    LayerTag.objects.filter(layer=layer).delete()
    LayerTag.objects.bulk_create([
        LayerTag(layer=layer, name=tag)
        for tag in form.cleaned_data['tags']
    ])

    # Update images.
    LayerImage.objects.filter(layer=layer).delete()
    LayerImage.objects.bulk_create([
        LayerImage(layer=layer, source_uri=uri)
        for uri in form.cleaned_data['images']
    ])

    return layer.to_json()


@owner_required
def _delete_layer(request, layer, username=None):
    layer.deleted_at = timezone.now()
    layer.save()
    return 'OK'


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
        # Ensure user can only favorite owned/public layers.
        _get_layer_or_404(request, id=layer_id)

        model, created = UserFavoriteLayer.objects.get_or_create(**kwargs)
        model.save()
    elif request.method == 'DELETE':
        model = get_object_or_404(UserFavoriteLayer, **kwargs)
        model.delete()
    return 'OK'


@api_view
@accepts('GET')
def all_layers(request):
    return _get_layers(request)


def _get_layer_models(request, crit=None):
    """
    Return list of filtered layer models.
    """
    qs = Layer.objects.select_related('user') \
                      .prefetch_related('layer_images', 'layer_tags',
                                        'favorites')

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
