import os

from rf.models import Image
from rf.utils.io import Visibility

from .io import get_geotiff_size_bytes, get_geotiff_resolution
from .create_bands import create_geotiff_bands


def create_geotiff_image(tif_path, sourceuri, filename=None,
                         visibility=Visibility.PRIVATE, imageMetadata={}, scene=None,
                         owner=None, band_create_function=create_geotiff_bands):
    """Create an Image object from a GeoTIFF.

    Args:
        tif_path (str): Local path to tif file
        sourceuri (str): remote source of image
        visibility (str): accessibility level for object
        imageMetadata (dict): Optional dict of metadata about the image
        scene (Scene): Optional Scene object holding this image
        owner (str): Optional owner of an image
        band_create_function (function): function to aid in creating bands for a geotiff
    """
    filename = filename if filename else os.path.basename(tif_path)
    return Image(
        get_geotiff_size_bytes(tif_path),
        visibility,
        filename,
        sourceuri,
        band_create_function(tif_path),
        imageMetadata,
        # TIFFs can have a different resolution in the X and Y directions, that is, pixels can be
        # rectangular with respect to the ground. The RF API doesn't currently support this, so just
        # select the X resolution.
        get_geotiff_resolution(tif_path)[0],
        [],
        scene=scene,
        owner=owner
    )
