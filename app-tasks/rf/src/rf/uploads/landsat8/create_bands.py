"""Creates band objects based on band lookup in Landsat 8 settings"""

import logging
from rf.models import Band

from .settings import band_lookup

logger = logging.getLogger(__name__)


def create_bands(resolution):
    """Create bands based on dict representation of bands in settings

    Args:
        resolution (str) resolution to create bands for (e.g. '15m')

    Returns:
        List[Band]
    """

    try:
        band_dicts = band_lookup[resolution]
    except KeyError:
        raise KeyError('No resolutions could be found for resolution %s', resolution)

    return [Band(**band) for band in band_dicts]
