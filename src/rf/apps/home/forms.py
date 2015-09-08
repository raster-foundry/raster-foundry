# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.forms import ModelForm

from apps.core.models import Layer


class LayerForm(ModelForm):

    class Meta:
        model = Layer
        fields = (
            'name',
            'description',
            'organization',
            'is_public',
            'capture_start',
            'capture_end',
            'area',
            'area_unit',
            'projection',
            'srid',
            'tile_srid',
            'tile_format',
            'tile_origin',
            'resampling',
            'transparency',
        )

    def clean(self):
        super(LayerForm, self).clean()

        # TODO: Parse tags from request (optional)
        try:
            self.cleaned_data['tags'] = self.data['tags']
        except:
            self.cleaned_data['tags'] = []

        # TODO: Parse images from request (required)
        try:
            self.cleaned_data['images'] = self.data['images']
        except:
            self.cleaned_data['images'] = []
