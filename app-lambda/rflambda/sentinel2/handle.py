import logging
from typing import Dict, Any

from rflambda.sentinel2.new_sentinel2_event import NewSentinel2Event
from rflambda.model import handler

logger = logging.getLogger(__name__)


def handle(event: Dict[str, Any], context: Dict[str, Any]):
    logger.info('Parsing s3 information from SNS event')
    logger.debug('Event: %s', event)
    parsed_event = NewSentinel2Event.parse(event)
    return handler(parsed_event, context)
