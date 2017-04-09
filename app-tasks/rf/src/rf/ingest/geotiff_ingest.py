import os
from urlparse import urlparse, ParseResult
from urllib import quote

from .models import Ingest, Source, Layer


layer_s3_bucket = os.getenv('TILE_SERVER_BUCKET')


def get_safe_uri(uri):
    """Parse URI and ensure the path is escaped properly

    Fixes a case where the URIs with the `|` are not escaped properly and break
    ingests

    Args:
        uri (str): uri to check for validity

    Returns:
        str
    """
    parsed = urlparse(uri)
    safe = ParseResult(parsed.scheme, parsed.netloc,
                       quote(parsed.path), parsed.params,
                       parsed.query, parsed.fragment)
    return safe.geturl()


def get_source_definition(image, extent, crs=None):
    """Create source definition from an image

    Args:
        image (Image): image to get source definition from
        extent (List[float]): extent of scene
        crs (str): optional crs of image

    Return:
        Source
    """

    uri = get_safe_uri(image.sourceUri)
    band_maps = [{'source': band.number, 'target': band.number} for band in image.bands]
    cell_size = {'width': image.resolutionMeters, 'height': image.resolutionMeters}
    extent_crs = 'epsg:4326'
    return Source(uri, extent, band_maps, cell_size, crs, extent_crs)


def get_ingest_layer(scene):
    """Create a layer definition for ingest

    Args:
        scene (Scene): scene to extract layer from

    Return:
        Layer
    """
    extent = scene.get_extent()
    sources = [get_source_definition(image, extent) for image in scene.images]
    highest_resolution_meters = min([image.resolutionMeters for image in scene.images])
    cell_size = {'width': highest_resolution_meters, 'height': highest_resolution_meters}
    output_uri = 's3://{}/layers'.format(layer_s3_bucket)
    return Layer(scene.id, output_uri, sources, cell_size)


def create_ingest_definition(scene):
    """Create ingest definition for a multiband geotiff

    Args:
         scene (Scene): scene to create an ingest definition for

    Return:
        Ingest
    """
    layer = get_ingest_layer(scene)
    id = scene.id
    return Ingest(id, [layer])
