# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import django_filters

from apps.core.models import Layer


class LayerFilter(django_filters.FilterSet):
    tag = django_filters.CharFilter(name='layer_tags__name')
    username = django_filters.CharFilter(name='user__username')

    class Meta:
        model = Layer
        fields = ('username', 'name', 'organization', 'tag')
        order_by = True
