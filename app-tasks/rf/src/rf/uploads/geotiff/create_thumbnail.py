import logging

logger = logging.getLogger(__name__)

def create_thumbnail(tif_path):
    logger.info('Creating Thumbnail for %s', tif_path)
    return tif_path
