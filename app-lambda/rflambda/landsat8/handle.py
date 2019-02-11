import logging
from typing import Dict, Any

from rflambda.landsat8.new_landsat8_event import NewLandsat8Event
from rflambda.model import handler

logger = logging.getLogger(__name__)


def handle(event: Dict[str, Any], context: Dict[str, Any]):
    logger.info('Parsing s3 information from SNS event')
    print('Landsat 8 SNS Event: {}'.format(event))
    parsed_event = NewLandsat8Event.parse(event)
    return handler(parsed_event, context)
