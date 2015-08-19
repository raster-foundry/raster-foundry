# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division


SQ_MI = 'sq. mi'
SQ_KM = 'sq. km'

AREA_UNIT_CHOICES = (
    (SQ_MI, SQ_MI),
    (SQ_KM, SQ_KM),
)

WGS84 = '4326'

PROJECTION_CHOICES = (
    (WGS84, 'WGS 84'),
)

SRID_CHOICES = (
    (WGS84, 'EPSG:4326'),
)

JPEG = 'jpeg'
PNG8 = 'png8'
PNG24 = 'png24'

TILE_FORMAT_CHOICES = (
    (JPEG, 'JPEG'),
    (PNG8, 'PNG 8-bit'),
    (PNG24, 'PNG 24-bit'),
)

TOPLEFT = 'topleft'
BOTTOMLEFT = 'bottomleft'

TILE_ORIGIN_CHOICES = (
    (TOPLEFT, 'OGC / WMTS / OpenStreetMap / Google XYZ (top-left origin)'),
    (BOTTOMLEFT, 'OSGEO TMS (bottom-left origin)'),
)

BILINEAR = 'bilinear'
CUBIC = 'cubic'

TILE_RESAMPLING_CHOICES = (
    (BILINEAR, 'Bilinear'),
    (CUBIC, 'Cubic'),
)
