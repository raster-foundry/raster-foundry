"""Extracts footprint from csv row for Landsat 8"""

from pyproj import Proj, transform

from rf.models import Footprint

from .settings import organization

# target projection for footprints
target_proj = Proj(init='epsg:4326')


def create_footprints(csv_row):
    """Extract footprint from a Landsat 8 csv row

    Args:
        csv_row (dict): dictionary representation of a row in scene list csv

    Returns:
        Footprint
    """
    src_proj = Proj(init='epsg:4326')
    ll = (csv_row['lowerLeftCornerLongitude'], csv_row['lowerLeftCornerLatitude'])
    lr = (csv_row['lowerRightCornerLongitude'], csv_row['lowerRightCornerLatitude'])
    ul = (csv_row['upperLeftCornerLongitude'], csv_row['upperLeftCornerLatitude'])
    ur = (csv_row['upperRightCornerLongitude'], csv_row['upperRightCornerLatitude'])
    src_coords = [(float(c[0]), float(c[1])) for c in [ll, lr, ur, ul]]

    min_x = min([coord[0] for coord in src_coords])
    min_y = min([coord[1] for coord in src_coords])
    max_x = max([coord[0] for coord in src_coords])
    max_y = max([coord[1] for coord in src_coords])

    transformed_coords = [[
        [transform(src_proj, target_proj, coord[0], coord[1]) for coord in src_coords]
    ]]
    data_footprint = Footprint(organization, transformed_coords)

    transformed_tile_coords = [[
        [transform(src_proj, target_proj, coord[0], coord[1]) for coord in
         [(min_x, min_y), (max_x, min_y), (max_x, max_y), (min_x, max_y)]]
    ]]
    tile_footprint = Footprint(organization, transformed_tile_coords)

    return tile_footprint, data_footprint
