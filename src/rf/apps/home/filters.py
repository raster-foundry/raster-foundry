# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from datetime import datetime

import django_filters

from django_filters import MethodFilter
from django.db.models import Q

from apps.core.models import Layer, LayerTag
from apps.core import enums


class LayerFilter(django_filters.FilterSet):
    tag = django_filters.CharFilter(name='layer_tags__name')
    username = django_filters.CharFilter(name='user__username')
    name_search = MethodFilter(action='name_tag_organization_filter')
    pending = MethodFilter(action='pending_filter')

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

    def pending_filter(self, queryset, value):
        """
        Gets layers that are uploading or processing, or that
        have become complete or failed since the time of the last browser
        refresh. This is so that layers that have become complete or failed
        since the last refresh will be visible to users on the frontend.

        value -- the time in milliseconds elapsed since 1/1/1970
        """
        refresh_time = datetime.fromtimestamp(float(value) / 1000.0)
        return queryset.filter((~Q(status=enums.STATUS_COMPLETED) &
                                ~Q(status=enums.STATUS_FAILED)) |
                               Q(status_updated_at__gt=refresh_time))
