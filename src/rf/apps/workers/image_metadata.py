# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import exifread

from apps.core.models import Layer


def calculate_and_save_layer_boundingbox(layer_id):
    layer = Layer.objects.get(id=layer_id)
    images = layer.layer_images.all()
    bounds = [
        min([i.min_x for i in images]),
        max([i.max_x for i in images]),
        min([i.min_y for i in images]),
        max([i.max_y for i in images])
    ]
    layer.set_bounds(bounds)


def get_image_exif_data(file_descriptor):
    # Setting details to false disables reading baked in thumbnail data.
    tags = exifread.process_file(file_descriptor, details=False)
    data = []
    for tag in tags.keys():
        data.append({'key': tag, 'value': str(tags[tag])})
    return data
