"""Extracts footprint from csv row for Landsat 8"""

from pyproj import Proj, transform

from rf.models import Footprint

from .settings import organization

# target projection for footprints
target_proj = Proj(init='epsg:3857')


def create_footprint(csv_row):
    """Extract footprint from a Landsat 8 csv row

    Args:
        csv_row (dict): dictionary representation of a row in scene list csv

    Returns:
        Footprint
    """
    src_proj = Proj(init='epsg:4326')
    coords = [
        [csv_row['lowerLeftCornerLongitude'], csv_row['lowerLeftCornerLatitude']],
        [csv_row['lowerRightCornerLongitude'], csv_row['lowerRightCornerLatitude']],
        [csv_row['upperRightCornerLongitude'], csv_row['upperRightCornerLatitude']],
        [csv_row['upperLeftCornerLongitude'], csv_row['upperLeftCornerLatitude']]
    ]

    transformed_coords = [[
        [transform(src_proj, target_proj, coord[0], coord[1]) for coord in coords]
    ]]
    geojson = {'type': 'MultiPolygon', 'coordinates': transformed_coords}
    return Footprint(organization, geojson)
