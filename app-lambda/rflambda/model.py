import logging
import os
from typing import Any, Dict, Union

from rasterfoundry.api import API  # type: ignore

from rflambda.landsat8.new_landsat8_event import NewLandsat8Event
from rflambda.sentinel2.new_sentinel2_event import NewSentinel2Event
import rflambda.landsat8.create_scene as create_l8
import rflambda.sentinel2.create_scene as create_s2

logger = logging.getLogger(__name__)

eventType = Union[NewLandsat8Event, NewSentinel2Event]


def handler(event: eventType, context: Dict[str, Any]):
    api_host = os.getenv('RF_API_HOST', 'app.rasterfoundry.com')
    refresh_token = os.getenv('RF_REFRESH_TOKEN')
    api_token = os.getenv('RF_API_TOKEN')
    logger.info('Connecting to Raster Foundry API')
    rf_api = API(
        refresh_token=refresh_token,
        api_token=api_token,
        host=api_host,
        scheme='https' if ('localhost' not in api_host) else 'http')
    logger.info('Creating scene from parsed SNS event')
    scene_to_post = create_l8.create_scene(event) if isinstance(
        event, NewLandsat8Event) else create_s2.create_scene(event)
    logger.info('Sending scene to the Raster Foundry API at %s', api_host)
    result = rf_api.client.Imagery.post_scenes(scene=scene_to_post).result()
    logger.info('Scene %s created successfully', event.scene_name)
    return f'Success - {event.scene_name} Created'
