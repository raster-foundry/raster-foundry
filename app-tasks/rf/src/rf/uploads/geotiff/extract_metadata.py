import logging

logger = logging.getLogger(__name__)

def extract_metadata(tif_path):
    logger.info('Extracting metadata for %s', tif_path)
    return tif_path
