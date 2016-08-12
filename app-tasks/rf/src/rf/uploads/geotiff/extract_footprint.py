import logging

logger = logging.getLogger(__name__)

def extract_footprint(tif_path):
    logger.info('Extracting footprint for %s', tif_path)
    return tif_path
