import functools
import json
import logging
import uuid
from typing import Any, Dict, List, Tuple

import boto3  # type: ignore
from pyproj import Proj  # type: ignore
from shapely.geometry import shape  # type: ignore

from rflambda.fields import FilterFields, Footprints
from rflambda.sentinel2.new_sentinel2_event import NewSentinel2Event

logger = logging.getLogger(__name__)
s3_client = boto3.client('s3')
band_order = {
    'B01': {
        "name": "coastal aerosol - 1",
        "number": 0,
        "wavelength": [433, 453],
        "resolution": 60
    },
    'B02': {
        "name": "blue - 2",
        "number": 0,
        "wavelength": [457, 523],
        "resolution": 10
    },
    'B03': {
        "name": "green - 3",
        "number": 0,
        "wavelength": [542, 578],
        "resolution": 10
    },
    'B04': {
        "name": "red - 4",
        "number": 0,
        "wavelength": [650, 680],
        "resolution": 10
    },
    'B05': {
        "name": "near infrared - 5",
        "number": 0,
        "wavelength": [697, 713],
        "resolution": 20
    },
    'B06': {
        "name": "near infrared - 6",
        "number": 0,
        "wavelength": [732, 748],
        "resolution": 20
    },
    'B07': {
        "name": "near infrared - 7",
        "number": 0,
        "wavelength": [773, 793],
        "resolution": 20
    },
    'B08': {
        "name": "near infrared - 8",
        "number": 0,
        "wavelength": [784, 900],
        "resolution": 10
    },
    'B8A': {
        "name": "near infrared - 8a",
        "number": 0,
        "wavelength": [855, 875],
        "resolution": 20
    },
    'B11': {
        "name": "short-wave infrared - 11",
        "number": 0,
        "wavelength": [1565, 1655],
        "resolution": 20
    },
    'B09': {
        "name": "water vapor - 9",
        "number": 0,
        "wavelength": [935, 955],
        "resolution": 60
    },
    'B10': {
        "name": "cirrus - 10",
        "number": 0,
        "wavelength": [1365, 1385],
        "resolution": 60
    },
    'B12': {
        "name": "short-wave infrared - 12",
        "number": 0,
        "wavelength": [2100, 2280],
        "resolution": 20
    }
}


@functools.lru_cache()
def load_json_metadata(event: NewSentinel2Event) -> Dict[str, Any]:
    logger.info('Loading metadata for %s from tile info json file',
                event.scene_name)
    return json.loads(
        s3_client.get_object(
            Bucket=event.bucket,
            Key=f'{event.prefix}/tileInfo.json',
            RequestPayer='requester')['Body'].read())


def metadata_from_json(
        event: NewSentinel2Event) -> Tuple[FilterFields, Footprints]:
    logger.info(
        'Fetching filter fields and footprints from Sentinel-2 product metadata'
    )
    tile_info = load_json_metadata(event)

    # Filter fields
    cloud_cover = tile_info['cloudyPixelPercentage']
    acquisition_date = tile_info['timestamp']
    filter_fields = FilterFields(cloud_cover, None, None, acquisition_date)

    # Footprints
    data_footprint_crs_string = tile_info['tileDataGeometry']['crs']['properties']['name']
    data_footprint_epsg = data_footprint_crs_string.split(':')[-1]
    data_footprint_proj = Proj(init=f'EPSG:{data_footprint_epsg}')
    data_footprint_native = shape(tile_info['tileDataGeometry'])
    logger.debug(
        'Number of points in native polygon exterior: %s',
        len(data_footprint_native.exterior.xy))
    simplified = data_footprint_native.simplify(0.05)
    logger.debug('Number of points in simplified polygon exterior: %s', len(simplified.exterior.xy))
    footprints = Footprints.from_shape(simplified, data_footprint_proj)

    return (filter_fields, footprints)


def images_from_event(event: NewSentinel2Event, scene_id: str) -> List[Dict[str, Any]]:
    return [
        dict(
            rawDataBytes=0,
            visibility='PUBLIC',
            filename=f'{k}.jp2',
            sourceUri=f'https://{event.bucket}.s3.amazonaws.com/{event.prefix}/{k}.jp2',
            owner=None,
            scene=scene_id,
            imageMetadata=None,
            resolutionMeters=band_order[k]['resolution'],
            metadataFiles=[],
            bands=[band_order[k]]
        ) for k in sorted(list(band_order.keys()))
    ]


def thumbnails_from_event(event: NewSentinel2Event, scene_id: str) -> List[Dict[str, Any]]:
    # magic numbers from scala import process
    widthPx = 343
    heightPx = 343
    return [
        dict(
            id=None,
            thumbnailSize='SQUARE',
            widthPx=widthPx,
            heightPx=heightPx,
            sceneId=scene_id,
            url=f'https://{event.bucket}.s3.amazonaws.com/{event.prefix}/preview.jpg'
        )
    ]


def get_freeform_metadata(event: NewSentinel2Event) -> Dict[str, Any]:
    tile_info = load_json_metadata(event)
    return {
        k: v for k, v in tile_info.items()
        if k not in ['tileDataGeometry', 'tileGeometry', 'datastrip']
    }


def create_scene(event: NewSentinel2Event) -> Dict[str, Any]:
    logger.info('Creating scene from event for %s', event.scene_name)
    filters, footprints = metadata_from_json(event)
    scene_id = str(uuid.uuid4())
    images = images_from_event(event, scene_id)
    thumbnails = thumbnails_from_event(event, scene_id)
    return dict(
        id=scene_id,
        visibility='Public',
        tags=['Sentinel-2', 'JPEG2000'],
        datasource={
            # ID of Landsat 8 datasource everywhere. See migration 38
            'id': '4a50cb75-815d-4fe5-8bc1-144729ce5b42',
            # These two fields are ignored, they're only present to make bravado happy
            'name': 'Sentinel-2',
            'bands': []
        },
        sceneMetadata=get_freeform_metadata(event),
        name=event.scene_name,
        owner=None,
        tileFootprint=footprints.tile_polygon,
        dataFootprint=footprints.data_polygon,
        metadataFiles=[
            event.tile_info_json,
            event.product_info_json,
            f'https://{event.bucket}.s3.amazonaws.com/{event.prefix}/metadata.xml'
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
