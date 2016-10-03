"""Extracts footprint from a tileinfo object from Sentinel 2"""

from pyproj import Proj, transform

from rf.models import Footprint

from .settings import organization


# target projection for footprints
target_proj = Proj(init='epsg:3857')

def get_src_proj(crs_string):
    """Helper function to return pyproj CRS from tileinfo CRS

    Args:
        crs_string (str): string represenatation of CRS from tileinfo.json (e.g. urn:ogc:def:crs:EPSG:8.8.1:32754)

    Returns:
        pyproj.Proj
    """
    return Proj(init='epsg:{}'.format(crs_string.split(':')[-1]))


def create_footprint(tileinfo):
    """Extracts footprint from a tileinfo dictionary

    Args:
        tileinfo (dict): dictionary represenation of tileInfo.json

    Returns:
        Footprint
    """
    geom = tileinfo['tileDataGeometry']
    coords = geom['coordinates'][0]
    src_proj = get_src_proj(geom['crs']['properties']['name'])
    transformed_coords = [[[transform(src_proj, target_proj, coord[0], coord[1]) for coord in coords]]]
    geojson = {"type": "MultiPolygon", "coordinates": transformed_coords}
    return Footprint(organization, geojson)
