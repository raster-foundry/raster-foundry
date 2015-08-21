# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import json

from django.contrib.auth.models import User
from django.shortcuts import (render_to_response,
                              get_object_or_404)
import django_filters

from rest_framework import (viewsets, mixins,
                            status, generics,
                            filters)
from rest_framework.decorators import detail_route
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated

from apps.core.models import Layer, LayerMeta, UserFavoriteLayer
from apps.core.serializers import LayerSerializer, LayerMetaSerializer

from django.conf import settings


def home_page(request):
    return render_to_response('home/home.html',
                              {'client_settings': get_client_settings()})


class LayerFilter(django_filters.FilterSet):
    tag = django_filters.CharFilter(name='layer_tags__name')

    class Meta:
        model = Layer
        fields = ('name', 'organization', 'tag')


# This class is derived from ViewSet (instead of ApiView) because
# it automatically routes /user/<username>/layers/<slug>/
# to destroy and retrieve, and /user/<username>/layers/
# to create and list.
class UserLayerViewSet(mixins.ListModelMixin,
                       mixins.RetrieveModelMixin,
                       mixins.CreateModelMixin,
                       mixins.DestroyModelMixin,
                       viewsets.GenericViewSet):
    serializer_class = LayerSerializer
    lookup_field = 'slug'
    permission_classes = [IsAuthenticated]

    filter_backends = (filters.DjangoFilterBackend, filters.OrderingFilter,)
    filter_class = LayerFilter
    ordering_fields = ('name', 'organization')

    def get_queryset(self):
        get_object_or_404(User, username=self.kwargs['username'])
        if self.request.user.username != self.kwargs['username']:
            return Layer.objects.filter(user__username=self.kwargs['username'],
                                        is_public=True)
        else:
            return Layer.objects.filter(user__username=self.kwargs['username'])

    def retrieve(self, request, *args, **kwargs):
        if (request.user.username != kwargs['username'] and not
                self.get_object().is_public):
            return Response(status=status.HTTP_404_NOT_FOUND)
        else:
            return super(UserLayerViewSet, self) \
                .retrieve(request, *args, **kwargs)

    def destroy(self, request, *args, **kwargs):
        if request.user.username != kwargs['username']:
            if self.get_object().is_public:
                return Response(status=status.HTTP_401_UNAUTHORIZED)
            return Response(status=status.HTTP_404_NOT_FOUND)
        else:
            return super(UserLayerViewSet, self) \
                .destroy(request, *args, **kwargs)

    def create(self, request, *args, **kwargs):
        if request.user.username != kwargs['username']:
            return Response(status=status.HTTP_404_NOT_FOUND)
        else:
            return super(UserLayerViewSet, self) \
                .create(request, *args, **kwargs)

    @detail_route()
    def meta(self, request, *args, **kwargs):
        get_object_or_404(User, username=self.kwargs['username'])
        if request.user.username != kwargs['username'] and \
           not self.get_object().is_public:
            return Response(status=status.HTTP_404_NOT_FOUND)
        else:
            layer_meta = LayerMeta.objects.filter(layer=self.get_object()) \
                                          .order_by('-created_at').first()
            if layer_meta:
                serializer = LayerMetaSerializer(layer_meta)
                return Response(serializer.data)
            else:
                return Response(status=status.HTTP_404_NOT_FOUND)


class LayerListView(generics.ListAPIView):
    serializer_class = LayerSerializer
    permission_classes = [IsAuthenticated]

    filter_backends = (filters.DjangoFilterBackend, filters.OrderingFilter,)
    filter_class = LayerFilter
    ordering_fields = ('name', 'organization')

    def get_queryset(self):
        return (Layer.objects.filter(user=self.request.user, is_public=False) |
                Layer.objects.filter(is_public=True))


class FavoriteListView(generics.ListAPIView):
    serializer_class = LayerSerializer
    permission_classes = [IsAuthenticated]

    filter_backends = (filters.DjangoFilterBackend, filters.OrderingFilter,)
    filter_class = LayerFilter
    ordering_fields = ('name', 'organization')

    def get_queryset(self):
        get_object_or_404(User, username=self.kwargs['username'])
        return Layer.objects.filter(
            favorites__user__username=self.kwargs['username'])

    def get(self, request, *args, **kwargs):
        if request.user.username != kwargs['username']:
            return Response(status=status.HTTP_404_NOT_FOUND)
        else:
            return super(FavoriteListView, self).get(request, *args, **kwargs)


class FavoriteCreateDestroyView(generics.GenericAPIView,
                                mixins.DestroyModelMixin):
    permission_classes = [IsAuthenticated]

    def get_layer(self):
        return get_object_or_404(Layer,
                                 user__username=self.kwargs['username'],
                                 slug=self.kwargs['slug'])

    def get_object(self):
        layer = self.get_layer()
        return get_object_or_404(UserFavoriteLayer,
                                 user=self.request.user,
                                 layer=layer)

    def post(self, request, *args, **kwargs):
        get_object_or_404(User, username=self.kwargs['username'])
        layer = self.get_layer()
        if layer and ((layer.user == self.request.user) or layer.is_public):
            UserFavoriteLayer.objects.create(
                user=self.request.user, layer=layer)
            return Response(status=status.HTTP_201_CREATED)
        return Response(status=status.HTTP_404_NOT_FOUND)

    def delete(self, request, *args, **kwargs):
        return self.destroy(request, *args, **kwargs)


def get_client_settings():
    client_settings = json.dumps({
        'signerUrl': settings.CLIENT_SETTINGS['signer_url'],
        'awsKey': settings.CLIENT_SETTINGS['aws_key'],
        'awsBucket': settings.CLIENT_SETTINGS['aws_bucket'],
    })
    return client_settings
