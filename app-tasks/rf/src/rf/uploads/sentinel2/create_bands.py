"""Creates band objects based on band lookup in Sentinel-2 settings"""

import logging
from rf.models import Band

from .settings import band_lookup

logger = logging.getLogger(__name__)


def create_bands(band_number):
    """Create bands based on dict representation of bands in settings
    Since Sentinel-2 only has one band per image, band_number is a single string.


    Args:
        band_number (str) Bxx representation of the band number to return

    Returns:
        List[Band]
    """
    try:
        band_dict = band_lookup[band_number]
    except KeyError:
        raise KeyError('Band %s does not exist', band_number)

    return [Band(**band_dict)]
