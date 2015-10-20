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
            self.cleaned_data['tags'] = [tag.strip()
                                         for tag in self.data['tags']]
        except:
            self.cleaned_data['tags'] = []

        try:
            self.cleaned_data['images'] = self.data['images']
        except:
            self.cleaned_data['images'] = []

        try:
            start = self.cleaned_data['capture_start']
        except KeyError:
            # Improperly formed date string.\
            start = None
            self.add_error('capture_start',
                           'The date is not valid or is not a valid format.')

        try:
            end = self.cleaned_data['capture_end']
        except KeyError:
            # Improperly formed date strings.
            end = None
            self.add_error('capture_end',
                           'The date is not valid or is not a valid format.')

        if start is not None and \
           end is not None and \
           (end - start).seconds < 0.0:
            self.add_error('capture_end',
                           'capture_end is before capture_start')
