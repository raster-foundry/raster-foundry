# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from urllib import urlencode
import json

from django.conf import settings
from django.contrib.auth.models import User
from django.db import transaction
from django.db.models import Q
from django.shortcuts import get_object_or_404, Http404, render_to_response
from django.template import RequestContext
from django.utils import timezone
from django.views.decorators.csrf import ensure_csrf_cookie
from django.core.paginator import Paginator, EmptyPage, PageNotAnInteger

from apps.core import enums
from apps.core.exceptions import Forbidden
from apps.core.decorators import (accepts, api_view, login_required,
                                  owner_required)
from apps.core.models import Layer, LayerImage, LayerTag, UserFavoriteLayer
from apps.home.forms import LayerForm
from apps.home.filters import LayerFilter
from apps.workers.sqs_manager import SQSManager
from apps.workers.process import JOB_COPY_IMAGE

RESULTS_PER_PAGE = 10
MAX_RESULTS_PER_PAGE = 100


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


@api_view
@login_required
@accepts('POST')
def layer_dismiss(request):
    user_id = request.user.id
    layer_id = request.POST.get('layer_id')
    layer = get_object_or_404(Layer, id=layer_id, user_id=user_id)
    if layer.status == enums.STATUS_FAILED:
        _delete_layer(request, layer, request.user.username)
    # TODO: Consider returning something indicitive of the result:
    # return {'status': 'deleted'}
    return 'OK'


def _get_layer_or_404(request, **kwargs):
    try:
        crit = Q(**kwargs)
        return _get_layer_models(request, crit)['layers'][0]
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
    body = json.loads(request.body)

    form = LayerForm(body, instance=layer)

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
        LayerImage(layer=layer,
                   s3_uuid=image['s3_uuid'],
                   file_extension=image['file_extension'],
                   file_name=image['file_name'],
                   bucket_name=settings.AWS_BUCKET_NAME,
                   source_s3_bucket_key=image['source_s3_bucket_key'])
        for image in form.cleaned_data['images']
    ])

    # Create jobs to copy images into S3 bucket
    if layer.has_copied_images():
        sqs_manager = SQSManager()
        for image in LayerImage.objects.filter(layer=layer):
            job_type = JOB_COPY_IMAGE
            data = {'image_id': image.id}
            sqs_manager.add_message(job_type, data)

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

    if request.GET.get('pending') is None:
        qs = qs.filter(status=enums.STATUS_COMPLETED)

    filtered_layers = LayerFilter(request.GET, queryset=qs)

    page = request.GET.get('page')
    page_size = request.GET.get('page_size')

    results_per_page = RESULTS_PER_PAGE
    if page_size:
        try:
            page_size = int(page_size)
            if page_size == 0:
                num_layers = filtered_layers.count()
                if num_layers > 0:
                    results_per_page = min(num_layers, MAX_RESULTS_PER_PAGE)
            else:
                results_per_page = min(page_size, MAX_RESULTS_PER_PAGE)
        except:
            pass

    paginator = Paginator(filtered_layers, results_per_page)
    try:
        layers = paginator.page(page)
    except PageNotAnInteger:
        # If page is not an integer, deliver first page.
        page = 1
        layers = paginator.page(page)
    except EmptyPage:
        # If page is out of range, deliver last page of results.
        page = paginator.num_pages
        layers = paginator.page(page)

    page = int(page)
    prev_url = (page - 1) if page > 1 else None
    next_url = (page + 1) if page < paginator.num_pages else None

    return {
        'layers': layers,
        'pages': paginator.num_pages,
        'current_page': page,
        'next_url': next_url,
        'prev_url': prev_url
    }


def _get_layers(request, crit=None):
    """
    Return list of JSON serializable layer models.
    """
    results = _get_layer_models(request, crit)
    models = [m.to_json() for m in results['layers']]
    prev_url = None
    next_url = None
    get = request.GET.copy()

    if results['prev_url'] is not None:
        get['page'] = results['prev_url']
        prev_url = request.path + '?' + urlencode(get)

    if results['next_url'] is not None:
        get['page'] = results['next_url']
        next_url = request.path + '?' + urlencode(get)

    return {
        'layers': models,
        'pages': results['pages'],
        'current_page': results['current_page'],
        'prev_url': prev_url,
        'next_url': next_url
    }
