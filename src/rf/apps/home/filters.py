# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import django_filters

from django_filters import MethodFilter
from django.db.models import Q

from apps.core.models import Layer, LayerTag


class LayerFilter(django_filters.FilterSet):
    tag = django_filters.CharFilter(name='layer_tags__name')
    username = django_filters.CharFilter(name='user__username')
    name_search = MethodFilter(action='name_tag_organization_filter')

    class Meta:
        model = Layer
        fields = ('username', 'name', 'organization', 'tag', 'created_at',
                  'capture_start', 'capture_end', 'area', 'srid')
        order_by = True

    def name_tag_organization_filter(self, queryset, value):
        layer_values = LayerTag.objects.filter(name__contains=value) \
                                       .values_list('layer_id', flat=True)

        return queryset.filter(Q(id__in=list(layer_values)) |
                               Q(name__icontains=value) |
                               Q(organization__icontains=value))
