# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.contrib.auth.models import User
from django.db import transaction

from rest_framework import serializers

from apps.core.models import (Layer, LayerImage, LayerTag,
                              LayerMeta)


class UserSerializer(serializers.ModelSerializer):
    class Meta:
        model = User
        fields = ('id', 'username')


# Allow parsing a LayerTag represented as 'tag' rather than
# the default {'name': 'tag'}.
class LayerTagSerializer(serializers.Serializer):
    def to_representation(self, obj):
        return obj.name

    def to_internal_value(self, data):
        return {'name': data}


# Allow parsing a LayerImage represented as 'http://example.com'
# rather than the default {'source_uri': 'http://example.com'}.
class LayerImageSerializer(serializers.Serializer):
    def to_representation(self, obj):
        return obj.source_uri

    def to_internal_value(self, data):
        return {'source_uri': data}


class LayerSerializer(serializers.ModelSerializer):
    class Meta:
        model = Layer

    user = UserSerializer(default=serializers.CurrentUserDefault(),
                          read_only=True)
    layer_images = LayerImageSerializer(many=True)
    layer_tags = LayerTagSerializer(many=True)

    @transaction.atomic
    def create(self, validated_data):
        # Following guide to writable nested serializers in DRF.
        layer_images = validated_data.pop('layer_images')
        layer_tags = validated_data.pop('layer_tags')
        layer = Layer.objects.create(**validated_data)

        for layer_image in layer_images:
            LayerImage.objects.create(layer=layer, **layer_image)

        for layer_tag in layer_tags:
            LayerTag.objects.create(layer=layer, **layer_tag)

        return layer


class LayerMetaSerializer(serializers.ModelSerializer):
    class Meta:
        model = LayerMeta
