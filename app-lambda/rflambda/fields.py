from typing import List, Optional, Tuple

from pyproj import Proj, transform  # type: ignore
from shapely.geometry import MultiPolygon, Polygon, mapping  # type: ignore


def shift_footprint(footprint: List[Tuple[float, float]]) -> MultiPolygon:
    intersects = len(
        [x for (x, _) in footprint if x > 0]) not in (len(footprint), 0)
    if not intersects:
        return MultiPolygon([Polygon(footprint)])
    else:
        west_hemisphere = Polygon([(180, -90), (360, -90), (360, 90),
                                   (180, 90), (180, -90)])
        east_hemisphere = Polygon([(0, -90), (180, -90), (180, 90), (0, 90),
                                   (0, -90)])
        new_poly = Polygon(
            [(x if x > 0 else x + 360, y) for x, y in footprint])
        western = Polygon(
            [(x - 360, y)
             for x, y in new_poly.intersection(west_hemisphere).exterior.coords])
        eastern = Polygon(new_poly.intersection(east_hemisphere).exterior.coords)
        return MultiPolygon(
            [western,
             new_poly.intersection(east_hemisphere)])


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

        data_poly = shift_footprint(data_footprint + [data_footprint[0]])
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
