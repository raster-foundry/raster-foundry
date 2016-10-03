"""Finds scenes on a given day to extract"""

import logging
import json

from .settings import bucket

logger = logging.getLogger(__name__)


# prefix of products keys
prefix_template = 'products/{year}/{month}/{day}/'


def get_tile_prefixes_from_productinfo(key):
    """Given an S3 key for a product ingo, returns a list of prefixes for scene tile info

    Args:
        key (s3.Object): s3 object that represents product info metadata for sentinel 2

    Returns:
        List[str]
    """
    product_info = json.loads(key.get()['Body'].read())
    return [tile['path'] for tile in product_info['tiles']]


def find_sentinel2_scenes(year, month, day):
    """Find new scenes that were added for a year month day

    Note: Does not create them, returns scene prefixes

    Args:
        year (int): year to search
        month (int): month to search
        day (int): day to search

    Returns:
        List[str]
    """
    logging.info('Searching for new scenes on %s-%s-%s', year, month, day)
    prefix = prefix_template.format(year=year, month=month, day=day)
    keys = [k for k in bucket.objects.filter(Prefix=prefix) if k.key.endswith('productInfo.json')]

    logging.info('Found %s products to create tiles from', len(keys))

    tile_prefixes = []

    for key in keys:
        tile_prefixes += get_tile_prefixes_from_productinfo(key)
    logging.info('Found %s tiles to import scenes from on %s-%s-%s', len(tile_prefixes), year, month, day)

    return tile_prefixes
