# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

STATUS_CHUNK = 'chunk'
STATUS_COMPLETED = 'completed'
STATUS_CREATE = 'create'
STATUS_CREATE_CLUSTER = 'cluster'
STATUS_FAILED = 'failed'
STATUS_MOSAIC = 'mosaic'
STATUS_THUMBNAIL = 'thumbnail'
STATUS_TRANSFER = 'transfer'
STATUS_VALIDATE = 'validate'

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

MERCATOR = 'mercator'
UTM = 'utm'
EPSG = 'epsg'
SRID_CHOICES = (
    (MERCATOR, 'Web Mercator'),
    (UTM, 'UTM'),
    (WGS84, 'EPSG:4326'),
    (EPSG, 'EPSG/ESRI offline database'),
)

JPEG = 'jpeg'
OVER_PNG8 = 'over_png8'
OVER_PNG32 = 'over_png32'
BASE_JPEG = 'base_jpeg'
BASE_PNG8 = 'base_png8'
BASE_PNG24 = 'base_png24'
TILE_FORMAT_CHOICES = (
    (JPEG, 'JPEG'),
    (OVER_PNG8, 'Overlay PNG + optimisation ' +
        '(8 bit palette with alpha transparency)'),
    (OVER_PNG32, 'Overlay PNG format (32 bit RGBA with alpha transparency)'),
    (BASE_JPEG, 'Base map JPEG format (without transparency)'),
    (BASE_PNG8, 'Base map PNG format + optimisation ' +
        '(8 bit palette with alpha transparency)'),
    (BASE_PNG24, 'Base map PNG format (24 bit RGB without transparency)'),
)

TOPLEFT = 'topleft'
BOTTOMLEFT = 'bottomleft'

TILE_ORIGIN_CHOICES = (
    (TOPLEFT, 'OGC / WMTS / OpenStreetMap / Google XYZ (top-left origin)'),
    (BOTTOMLEFT, 'OSGEO TMS (bottom-left origin)'),
)

BILINEAR = 'bilinear'
CUBIC = 'cubic'
CUBIC_BSPLINE = 'cubic_bspline'
AVERAGE = 'average'
MODE = 'mode'
NEAREST_NEIGHBOR = 'nearest_neighbor'

TILE_RESAMPLING_CHOICES = (
    (BILINEAR, 'Bilinear'),
    (CUBIC, 'Cubic'),
    (CUBIC_BSPLINE, 'Cubic B-Spline'),
    (AVERAGE, 'Average'),
    (MODE, 'Mode'),
    (NEAREST_NEIGHBOR, 'Nearest Neighbor'),
)
