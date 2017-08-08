import re
import os
import requests

from .models import Ingest, Source, Layer


layer_s3_bucket = os.getenv('TILE_SERVER_BUCKET')


def parse_mtl(mtl_file_str):
    """Parses MTL file into a dictionary

    Args:
        mtl_file_str (str): MTL file loaded as a string

    Returns:
        dict
    """
    lines = mtl_file_str.split('\n')
    mtl_dict = {}
    for l in lines:
        split_line = l.split('=')
        if len(split_line) == 2:
            mtl_dict[split_line[0].strip()] = split_line[1].strip()
    return mtl_dict


def get_crs(scene):
    """Gets MTL file for a scene and calculates CRS

    Notes:
        This is _fairly_ specific to Landsat8 in S3 right now.
        All Landsat 8 scenes have UTM codes in the Northern hemisphere (prefix = 326),
        which obviously isn't correct.

    Returns:
        str
    """
    mtl_url = scene.metadataFiles[0]
    response = requests.get(mtl_url)
    mtl_dict = parse_mtl(response.text)
    return 'epsg:326{}'.format(mtl_dict['UTM_ZONE'])


def get_band_num(image):
    """Extract band number from a Landsat 8 image

    Args:
        image (dict): image representation in RF API

    Returns:
        int
    """
    filename = image.filename
    m = re.search('LC.*B(\d+).*', filename)
    return int(m.group(1))


def get_source(image, crs, extent):
    """Constructs source for a Landsat 8 image

    Args:
        image (dict): image representing a tif for Landsat 8
        crs (str): crs of scene
        extent (list[int]): extent of scene

    Return:
        Source
    """
    uri = image.sourceUri
    band_maps = [{'source': 1, 'target': get_band_num(image)}]
    cell_size = {'width': image.resolutionMeters,
                 'height': image.resolutionMeters}
    return Source(uri, band_maps, cell_size)


def get_landsat8_layer(scene):
    """Construct layer for Landsat 8 scene

    Args:
        scene (Scene): Landsat 8 scene to construct layer for

    Returns:
        Layer
    """
    extent = scene.get_extent()
    crs = get_crs(scene)
    output_uri = 's3://{}/layers'.format(layer_s3_bucket)
    sources = [get_source(image, crs, extent) for image in scene.images]
    cell_size = {'width': 30, 'height': 30}
    return Layer(scene.id, output_uri, sources, cell_size)


def create_landsat8_ingest(scene):
    """Create Landsat 8 ingest definition from scene

    Args:
        scene (Scene): scene to create ingest definition for

    Returns:
        Ingest
    """
    layer = get_landsat8_layer(scene)
    id = scene.id
    return Ingest(id, [layer])
