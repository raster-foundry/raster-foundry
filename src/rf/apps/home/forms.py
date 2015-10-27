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
            for image in self.data['images']:
                if 'source_s3_bucket_key' not in image:
                    image['source_s3_bucket_key'] = None
                self.cleaned_data['images'] = self.data['images']
        except:
            self.cleaned_data['images'] = []

        start = self.cleaned_data.get('capture_start', None)
        end = self.cleaned_data.get('capture_end', None)

        if start and not end:
            self.add_error('capture_end',
                           'The date needs to be provided.')
        if end and not start:
            self.add_error('capture_start',
                           'The date needs to be provided.')

        if start is not None and \
           end is not None and \
           end < start:
            self.add_error('capture_end',
                           'capture_end is before capture_start')
