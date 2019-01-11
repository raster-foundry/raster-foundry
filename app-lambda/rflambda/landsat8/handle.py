import logging
import os
from typing import Dict, Any

from rasterfoundry.api import API  # type: ignore

from rflambda.landsat8.new_landsat8_event import NewLandsat8Event
from rflambda.landsat8.create_scene import create_scene

logger = logging.getLogger(__name__)


def handle(event: Dict[str, Any], context: Dict[str, Any]):
    api_host = os.getenv('RF_API_HOST', 'app.rasterfoundry.com')
    refresh_token = os.getenv('RF_REFRESH_TOKEN')
    api_token = os.getenv('RF_API_TOKEN')
    logger.info('Connecting to Raster Foundry API')
    rf_api = API(
        refresh_token=refresh_token,
        api_token=api_token,
        host=api_host,
        scheme='https' if ('localhost' not in api_host) else 'http')
    logger.info('Parsing s3 information from SNS event')
    parsed_event = NewLandsat8Event.parse(event)
    logger.info('Creating scene from parsed SNS event')
    scene_to_post = create_scene(parsed_event)
    logger.info('Sending scene to the Raster Foundry API at %s', api_host)
    rf_api.client.Imagery.post_scenes(scene=scene_to_post).result()
    logger.info('Scene %s created successfully', parsed_event.scene_name)
