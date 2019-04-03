from shapely.geometry import MultiPolygon, Polygon, shape

from rflambda.fields import Footprints


def test_footprint_shift():
    bbox = [(179, 1), (-179, 1), (-179, 2), (179, 2)]
    bad_poly = MultiPolygon(
        [Polygon(bbox + [bbox[0]])]
    )
    footprints = Footprints(bbox)
    assert shape(footprints.tile_polygon).area < bad_poly.area
