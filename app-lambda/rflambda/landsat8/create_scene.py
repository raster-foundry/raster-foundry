import functools
import json
import logging
import os
import re
from typing import Any, Dict, List, Tuple
import uuid

import boto3  # type: ignore
from shapely.geometry import MultiPolygon, Polygon, mapping  # type: ignore

from rflambda.landsat8.new_landsat8_event import NewLandsat8Event

logger = logging.getLogger(__name__)
s3_client = boto3.client('s3')
band_order = {
    1: {
        'name': 'coastal aerosol - 1',
        'number': 0,
        'wavelength': [430, 450]
    },
    2: {
        'name': 'blue - 2',
        'number': 0,
        'wavelength': [450, 510]
    },
    3: {
        'name': 'green - 3',
        'number': 0,
        'wavelength': [530, 590]
    },
    4: {
        'name': 'red - 4',
        'number': 0,
        'wavelength': [640, 670]
    },
    5: {
        'name': 'near infrared - 5',
        'number': 0,
        'wavelength': [850, 880]
    },
    6: {
        'name': 'swir - 6',
        'number': 0,
        'wavelength': [1570, 1650]
    },
    7: {
        'name': 'swir - 7',
        'number': 0,
        'wavelength': [2110, 2290]
    },
    8: {
        'name': 'panchromatic - 8',
        'number': 0,
        'wavelength': [500, 680]
    },
    9: {
        'name': 'cirrus - 9',
        'number': 0,
        'wavelength': [1360, 1380]
    },
    10: {
        'name': 'thermal infrared - 10',
        'number': 0,
        'wavelength': [10600, 11190]
    },
    11: {
        'name': 'thermal infrared - 11',
        'number': 0,
        'wavelength': [11500, 12510]
    }
}


class FilterFields(object):
    def __init__(self, cloud_cover: float, sun_azimuth: float,
                 sun_elevation: float, acquisition_date: str):
        self.cloud_cover = cloud_cover
        self.sun_azimuth = sun_azimuth
        self.sun_elevation = sun_elevation
        self.acquisition_date = acquisition_date

    def to_dict(self):
        return dict(
            cloudCover=self.cloud_cover,
            sunAzimuth=self.sun_azimuth,
            sunElevation=self.sun_elevation,
            acquisitionDate=self.acquisition_date)


class Footprints(object):
    def __init__(self, data_footprint: List[Tuple[float, float]]):
        """Construct data and tile footprints using the points from product metadata

        Points are assumed to be in ll, lr, ur, ul order
        """

        data_poly = MultiPolygon(
            [Polygon(data_footprint + [data_footprint[0]])])
        tile_poly = MultiPolygon([data_poly.envelope])
        data_polygon = mapping(data_poly)
        tile_polygon = mapping(tile_poly)
        self.data_polygon = data_polygon
        self.tile_polygon = tile_polygon


def get_image_resolution(band_number: int) -> int:
    if band_number == '8':
        return 15
    else:
        return 30


def images_from_event(event: NewLandsat8Event, scene_id: str):
    mtl = load_json_metadata(event)
    product_metadata = mtl['L1_METADATA_FILE']['PRODUCT_METADATA']
    key_matcher = re.compile(r'FILE_NAME_BAND_(\d+)')
    image_files = [(k.split('_')[-1], f) for k, f in product_metadata.items()
                   if key_matcher.match(k)]
    return [
        dict(
            rawDataBytes=0,
            visibility='PUBLIC',
            filename=image[1],
            sourceUri=os.path.join(
                'https://' + event.bucket + '.s3.amazonaws.com', event.prefix,
                image[1]),
            owner=None,
            scene=scene_id,
            imageMetadata=None,
            resolutionMeters=get_image_resolution(image[0]),
            metadataFiles=[],
            bands=[band_order[int(image[0])]]) for image in image_files
    ]


@functools.lru_cache()
def load_json_metadata(event: NewLandsat8Event) -> Dict[str, Any]:
    logger.info('Loading metadata for %s from MTL json file', event.scene_name)
    return json.loads(
        s3_client.get_object(Bucket=event.bucket,
                             Key=event.mtl_json)['Body'].read())


def metadata_from_json(
        event: NewLandsat8Event) -> Tuple[FilterFields, Footprints]:
    logger.info(
        'Fetching filter fields and footprints from friendly format file')
    mtl = load_json_metadata(event)
    image_attrs = mtl['L1_METADATA_FILE']['IMAGE_ATTRIBUTES']
    product_metadata = mtl['L1_METADATA_FILE']['PRODUCT_METADATA']

    # Filter fields
    sun_azimuth = image_attrs['SUN_AZIMUTH']
    sun_elevation = image_attrs['SUN_ELEVATION']
    cloud_cover = image_attrs['CLOUD_COVER']
    acquisition_date = product_metadata['DATE_ACQUIRED']
    filter_fields = FilterFields(cloud_cover, sun_azimuth, sun_elevation,
                                 acquisition_date + 'T00:00:00Z')
    logger.debug('Filter fields are: %s', json.dumps(filter_fields.to_dict()))

    # Footprints
    points = [(product_metadata['CORNER_LL_LON_PRODUCT'],
               product_metadata['CORNER_LL_LAT_PRODUCT']),
              (product_metadata['CORNER_LR_LON_PRODUCT'],
               product_metadata['CORNER_LR_LAT_PRODUCT']),
              (product_metadata['CORNER_UR_LON_PRODUCT'],
               product_metadata['CORNER_UR_LAT_PRODUCT']),
              (product_metadata['CORNER_UL_LON_PRODUCT'],
               product_metadata['CORNER_UL_LAT_PRODUCT'])]
    footprints = Footprints(points)
    logger.debug('Calculated data footprint is: %s',
                 json.dumps(footprints.data_polygon))
    logger.debug('Calculated tile footprint is: %s',
                 json.dumps(footprints.tile_polygon))

    return (filter_fields, footprints)


def thumbnails_from_event(event: NewLandsat8Event,
                          scene_id: str) -> List[Dict[str, Any]]:
    # Magic numbers from scala import process
    small_size = (228, 233)
    big_size = (1143, 1168)
    small_url = 'https://' + os.path.join(
        event.bucket + '.s3.amazonaws.com', event.prefix,
        event.scene_name + '_thumb_small.jpg')
    big_url = 'https://' + os.path.join(event.bucket + '.s3.amazonaws.com',
                                        event.prefix,
                                        event.scene_name + '_thumb_large.jpg')
    return [
        dict(
            id=None,
            thumbnailSize='SMALL',
            widthPx=small_size[0],
            heightPx=small_size[1],
            sceneId=scene_id,
            url=small_url),
        dict(
            id=None,
            thumbnailSize='LARGE',
            widthPx=big_size[0],
            heightPx=big_size[1],
            sceneId=scene_id,
            url=big_url)
    ]


def get_freeform_metadata(event: NewLandsat8Event) -> Dict[str, Any]:
    js = load_json_metadata(event)
    metadata = js['L1_METADATA_FILE']
    return {
        k: v
        for k, v in list(metadata['IMAGE_ATTRIBUTES'].items()) +  # noqa: W504
        list(metadata['TIRS_THERMAL_CONSTANTS'].items()) +  # noqa: W504
        list(metadata['RADIOMETRIC_RESCALING'].items()) +  # noqa: W504
        list(metadata['PRODUCT_METADATA'].items()) +  # noqa: W504
        list(metadata['PROJECTION_PARAMETERS'].items()) +  # noqa: W504
        list(metadata['METADATA_FILE_INFO'].items()) +  # noqa: W504
        list(metadata['MIN_MAX_PIXEL_VALUE'].items()) +  # noqa: W504
        list(metadata['MIN_MAX_RADIANCE'].items()) +  # noqa: W504
        list(metadata['MIN_MAX_REFLECTANCE'].items())
    }


def create_scene(event: NewLandsat8Event) -> Dict[str, Any]:
    logger.info('Creating scene from event for %s', event.scene_name)
    filters, footprints = metadata_from_json(event)
    scene_id = str(uuid.uuid4())
    images = images_from_event(event, scene_id)
    thumbnails = thumbnails_from_event(event, scene_id)
    return dict(
        id=scene_id,
        visibility='Public',
        tags=['Landsat 8', 'GeoTIFF'],
        datasource={
            # ID of Landsat 8 datasource everywhere. See migration 38
            'id': '697a0b91-b7a8-446e-842c-97cda155554d',
            # These two fields are ignored, they're only present to make bravado happy
            'name': 'Landsat 8',
            'bands': []
        },
        sceneMetadata=get_freeform_metadata(event),
        name=event.scene_name,
        owner=None,
        tileFootprint=footprints.tile_polygon,
        dataFootprint=footprints.data_polygon,
        metadataFiles=[
            event.mtl_json,
            os.path.join(event.bucket, event.prefix, '{}_MTL.txt'.format(
                event.scene_name)),
        ],
        images=images,
        thumbnails=thumbnails,
        ingestLocation=None,
        filterFields=filters.to_dict(),
        statusFields={
            'thumbnailStatus': 'SUCCESS',
            'boundaryStatus': 'SUCCESS',
            'ingestStatus': 'NOTINGESTED'
        },
        sceneType=None)
