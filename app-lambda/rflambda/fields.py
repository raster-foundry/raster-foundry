from typing import List, Optional, Tuple

from pyproj import Proj, transform  # type: ignore
from shapely.geometry import MultiPolygon, Polygon, mapping  # type: ignore


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

        data_poly = MultiPolygon(
            [Polygon(data_footprint + [data_footprint[0]])])
        tile_poly = MultiPolygon([data_poly.envelope])
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
