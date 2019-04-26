from typing import List, Optional, Tuple

from pyproj import Proj, transform  # type: ignore
from shapely.geometry import MultiPolygon, Polygon, mapping  # type: ignore
from enum import Enum


class Side(Enum):
    LEFT = -1
    RIGHT = 1


def shift_point(
        point: Tuple[float, float], split: float, side: Side,
        inclusive: bool, degrees: float) -> Tuple[float, float]:
    """

    Args:
      point - point which will be shifted
      split - Shift points which are to the left of the split longitude
      side - Shift points on the specified side of the split
      inclusive - Shift points on the split line
      degrees - Degrees longitude to shift a point (positive and negative allowed)
    """
    x, y = point[0], point[1]
    if inclusive:
        if x * side.value <= split * side.value:
            return (x + degrees, y)
        else:
            return point
    else:
        if x * side.value < split * side.value:
            return (x + degrees, y)
        else:
            return point


def intersects_antimeridian(footprint: List[Tuple[float, float]]) -> bool:
    """
    This does a very simple check:
    Reproject the polygon to LatLng
    Shift points in the polygon which have negative values by adding 360 to them so the projection
        is now on a 0 to 360 degrees projection instead of -180 to 180
    If the resulting polygon is smaller, then we assume the polygon crosses the antimeridian.
    We can make this assumption because Landsat and Sentinel do not record imagery in sections which
    span a large enough portion of the globe to break the assumption
    We define the anti-meridian as Line(Point(180, -90), Point(180, 90)) because
        in LatLng, it's Line(Point(-180, -90), Point(-180, 90)) and we add 360 to it

    Eg: A polygon with a left bound of Point(-179, 1) and a right bound of Point(179, 1)
        would stretch across the entire map. The shifted bounds of Point(179, 1) and Point(181, 1)
        have a smaller area, so we split the polygon and shift the half which is > 180
        backward 360 degrees

        A polygon with bounds Point(-1, 1) and Point(1, 1) when shifted will be Point(1, 1)
        and Point(359, 1). This crosses the anti-meridian, but creates a shape which is larger
        than the original. We therefore conclude that it does not need splitting.
    """
    shifted_footprint = [shift_point(p, 0, Side.RIGHT, False, 360) for p in footprint]
    return Polygon(footprint).area - 1 > Polygon(shifted_footprint).area


def normalize_footprint(footprint: List[Tuple[float, float]]) -> MultiPolygon:
    """Split footprints which cross the anti-meridian

    Most applications, including RasterFoundry, cannot handle a single polygon representation
    of anti-meridian crossing footprint.
    Normalizing footprints by splitting them over the anti-meridian fixes cases where
    scenes appear to span all longitudes outside their actual footprint.
    If a footprint covers the anti-meridian, the shape is shifted 360 degrees, split,
    then the split section is moved back.
    """
    intersects = intersects_antimeridian(footprint)
    if not intersects:
        return MultiPolygon([Polygon(footprint)])
    else:
        shifted_footprint = Polygon([shift_point(p, 0, Side.RIGHT, False, 360) for p in footprint])
        left_hemisphere_mask = Polygon([(0, -90), (180, -90), (180, 90), (0, 90), (0, -90)])
        left_split = shifted_footprint.intersection(left_hemisphere_mask)
        right_split = Polygon([
            shift_point((x, y), 180, Side.LEFT, True, -360)
            for x, y
            in shifted_footprint.difference(left_hemisphere_mask).exterior.coords
        ])
        if left_split.area > 0 and right_split.area > 0:
            return MultiPolygon([left_split, right_split])
        elif left_split.area > 0:
            return MultiPolygon([left_split])
        elif right_split.area > 0:
            return MultiPolygon([right_split])
        else:
            return MultiPolygon([])


class FilterFields(object):
    def __init__(self, cloud_cover: Optional[float],
                 sun_azimuth: Optional[float], sun_elevation: Optional[float],
                 acquisition_date: Optional[str]):
        self.cloud_cover = cloud_cover
        self.sun_azimuth = sun_azimuth
        self.sun_elevation = sun_elevation
        self.acquisition_date = acquisition_date

    def to_dict(self):
        return dict(
            cloudCover=self.cloud_cover,
            sunAzimuth=self.sun_azimuth,
            sunElevation=self.sun_elevation,
            acquisitionDate=self.acquisition_date)


class Footprints(object):
    def __init__(self, data_footprint: List[Tuple[float, float]]):
        """Construct data and tile footprints using the points from product metadata

        Points are assumed to be in ll, lr, ur, ul order
        """

        data_poly = normalize_footprint(data_footprint + [data_footprint[0]])
        tile_poly = MultiPolygon([x.envelope for x in data_poly])
        data_polygon = mapping(data_poly)
        tile_polygon = mapping(tile_poly)
        self.data_polygon = data_polygon
        self.tile_polygon = tile_polygon

    @classmethod
    def from_shape(cls, shape: Polygon, source_crs: Proj):
        """Construct data and tile footprints from a data footprint shape in a given crs
        """

        exterior = shape.exterior.coords
        reprojected_exterior = [
            transform(source_crs, Proj(init='EPSG:4326'), p[0], p[1])
            for p in exterior
        ]
        return Footprints(reprojected_exterior)
